package dev.cipherkey.core.p2p;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.cipherkey.core.crypto.VaultCrypto;
import dev.cipherkey.core.merge.Merger;
import dev.cipherkey.core.model.Record;
import dev.cipherkey.core.model.SyncState;
import dev.cipherkey.core.sync.DeviceKey;
import dev.cipherkey.core.sync.SyncEngine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.LongSupplier;

/**
 * Sincronización directa entre dos dispositivos ya autenticados por {@link SecureChannel}.
 *
 * <p>Confianza: la primera vez que se ven, ambos muestran el mismo código de seguridad de 6 dígitos y el usuario confirma
 * en los dos; entonces cada uno fija la clave pública Ed25519 del otro. Las siguientes veces basta la clave fijada. Si
 * una clave fijada cambia, se aborta (posible suplantación).
 *
 * <p>Datos: se intercambian resúmenes (uuid + hash del contenido), cada lado pide lo que le falta o difiere y los
 * registros se fusionan a tres bandas con {@link Merger} usando como base lo último que tuvo ese par (o, en el primer
 * contacto, la última versión conocida del servidor). Nada se descarta: un conflicto se conserva y lo resuelve el usuario.
 * Todo lo cambiado se marca para subir también al servidor.
 */
public final class PeerSync {
    private PeerSync() {}

    public record Result(String peerId, String peerName, int received, int sent, int conflicts, boolean newlyTrusted) {}

    /** Pide al usuario comparar el código en ambos dispositivos. Devuelve true si coincide. */
    @FunctionalInterface
    public interface SasConfirm { boolean confirm(String peerName, String sas) throws Exception; }

    private static final int MAX_RECORDS = 100_000;

    /**
     * @param allowNewPeer solo true cuando el usuario ha pedido vincular (iniciador) o ha abierto el modo vincular (respondedor)
     */
    public static Result run(SecureChannel ch, boolean initiator, Locked doc, boolean allowNewPeer, SasConfirm confirm,
                             LongSupplier clock) throws Exception {
        SecureChannel.Peer peer = ch.peer();
        String[] self = doc.run(d -> new String[]{d.sync.deviceId});
        if (peer.deviceId().equals(self[0])) throw new PeerAuthException("El otro extremo dice ser este mismo dispositivo");

        // 1. confianza
        boolean pinned = doc.run(d -> {
            SyncState.Peer p = d.sync.peers.get(peer.deviceId());
            if (p == null) return false;
            if (!p.publicKey.equals(peer.publicKeyB64()))
                throw new PeerAuthException("La clave de «" + p.name + "» ha cambiado: posible suplantación. Desvincúlalo y vuelve a vincularlo");
            return true;
        });
        ch.send(new Frame("hello").put("pinned", pinned).put("accepts", allowNewPeer));
        Frame hello = expect(ch, "hello");
        boolean theirPinned = hello.json.get("pinned").getAsBoolean();
        boolean newly = false;
        if (!pinned || !theirPinned) {
            if (!allowNewPeer || !hello.json.get("accepts").getAsBoolean()) {
                ch.send(new Frame("deny").put("reason", "Dispositivo no vinculado: abre «Vincular dispositivo cercano» en ambos"));
                throw new PeerAuthException("Dispositivo no vinculado: abre «Vincular dispositivo cercano» en ambos dispositivos");
            }
            boolean ok = confirm != null && confirm.confirm(peer.name(), peer.sas());
            ch.send(new Frame("trust").put("ok", ok));
            Frame theirs = expect(ch, "trust");
            if (!ok || !theirs.json.get("ok").getAsBoolean()) throw new PeerAuthException("Código de seguridad rechazado");
            doc.run(d -> {
                SyncState.Peer p = d.sync.peers.computeIfAbsent(peer.deviceId(), k -> new SyncState.Peer());
                p.name = peer.name();
                p.publicKey = peer.publicKeyB64();
                return null;
            });
            newly = !pinned;
        }

        // 2. resúmenes
        record Snap(Map<String, Record> byUuid, Map<String, String> hash) {}
        Snap snap = doc.run(d -> {
            Map<String, Record> m = new LinkedHashMap<>();
            Map<String, String> h = new LinkedHashMap<>();
            for (Record r : d.records) {
                if (r.conflict != null) continue; // no se ofrece nada que esté pendiente de resolver
                Record c = r.copy();
                m.put(c.uuid, c);
                h.put(c.uuid, contentHash(c));
            }
            return new Snap(m, h);
        });
        JsonObject digest = new JsonObject();
        snap.hash().forEach(digest::addProperty);
        ch.send(new Frame("digest").put("d", digest));
        Frame theirDigest = expect(ch, "digest");
        JsonObject td = theirDigest.json.getAsJsonObject("d");
        if (td.size() > MAX_RECORDS) throw new PeerAuthException("Resumen demasiado grande");

        // 3. petición
        Map<String, Record> localNow = snap.byUuid();
        JsonArray want = new JsonArray();
        List<String> wanted = new ArrayList<>();
        for (var e : td.entrySet()) {
            String mine = snap.hash().get(e.getKey());
            if (mine == null || !mine.equals(e.getValue().getAsString())) { want.add(e.getKey()); wanted.add(e.getKey()); }
        }
        ch.send(new Frame("want").put("u", want));
        Frame theirWant = expect(ch, "want");

        // 4. registros
        JsonArray give = new JsonArray();
        List<String> givenIds = new ArrayList<>();
        final Snap snapNow = snap;
        int sent = 0;
        for (JsonElement u : theirWant.json.getAsJsonArray("u")) {
            Record r = localNow.get(u.getAsString());
            if (r == null) continue;
            give.add(r.toJson());
            givenIds.add(r.uuid);
            sent++;
        }
        ch.send(new Frame("records").put("r", give));
        Frame theirRecords = expect(ch, "records");

        // 5. aplicar con la bóveda en su estado actual
        Set<String> allowed = new HashSet<>(wanted);
        final int[] counts = new int[2];
        doc.run(d -> {
            SyncState st = d.sync;
            SyncState.Peer ps = st.peers.get(peer.deviceId());
            long now = clock.getAsLong();
            for (JsonElement el : theirRecords.json.getAsJsonArray("r")) {
                Record remote = Record.fromJson(el.getAsJsonObject());
                if (!allowed.contains(remote.uuid)) continue; // solo lo pedido
                remote.conflict = null;
                Optional<Record> localOpt = d.find(remote.uuid);
                if (localOpt.isEmpty()) {
                    d.records.add(remote.copy());
                    SyncEngine.markDirty(d, remote);
                    counts[0]++;
                } else {
                    Record local = localOpt.get();
                    if (local.conflict != null) continue;
                    Record base = ps.base.get(remote.uuid);
                    if (base == null) { SyncState.Baseline b = st.baselines.get(remote.uuid); if (b != null) base = b.base(); }
                    Merger.Result res = Merger.merge(base, local, remote, st.deviceId, now);
                    if (!contentHash(res.record()).equals(contentHash(local)) || res.hasConflict()) {
                        d.records.set(d.records.indexOf(local), res.record());
                        SyncEngine.markDirty(d, res.record());
                        counts[0]++;
                    }
                    if (res.hasConflict()) counts[1]++;
                }
                ps.base.put(remote.uuid, remote.copy());
            }
            // lo que ya coincide, o que acabamos de entregar tal cual, es el nuevo punto común con este par
            for (var e : td.entrySet()) {
                Record mine = snapNow.byUuid().get(e.getKey());
                if (mine != null && e.getValue().getAsString().equals(snapNow.hash().get(e.getKey()))) ps.base.put(e.getKey(), mine.copy());
            }
            for (String u : givenIds) { Record mine = snapNow.byUuid().get(u); if (mine != null) ps.base.putIfAbsent(u, mine.copy()); }
            ps.lastSync = now;
            return null;
        });

        ch.send(new Frame("done"));
        expect(ch, "done");
        return new Result(peer.deviceId(), peer.name(), counts[0], sent, counts[1], newly);
    }

    private static Frame expect(SecureChannel ch, String type) throws IOException {
        Frame f = ch.receive();
        if (f.type().equals("deny")) throw new PeerAuthException(f.get("reason"));
        if (!f.type().equals(type)) throw new PeerAuthException("Trama inesperada (" + f.type() + ", se esperaba " + type + ")");
        return f;
    }

    /** Hash del contenido sincronizable (sin metadatos de edición): dos registros iguales dan el mismo resumen. */
    public static String contentHash(Record r) {
        JsonObject o = new JsonObject();
        o.addProperty("uuid", r.uuid);
        o.addProperty("deleted", r.deleted);
        o.addProperty("title", r.title); o.addProperty("username", r.username); o.addProperty("password", r.password);
        o.addProperty("url", r.url); o.addProperty("notes", r.notes); o.addProperty("category", r.category);
        o.addProperty("favorite", r.favorite); o.addProperty("totpSecret", r.totpSecret);
        JsonArray tags = new JsonArray(); r.tags.forEach(tags::add); o.add("tags", tags);
        JsonArray custom = new JsonArray();
        for (Record.Custom c : r.custom) {
            JsonObject x = new JsonObject();
            x.addProperty("n", c.name()); x.addProperty("v", c.value()); x.addProperty("s", c.secret());
            custom.add(x);
        }
        o.add("custom", custom);
        return DeviceKey.hex(VaultCrypto.sha256(o.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
