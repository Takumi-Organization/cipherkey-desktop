package dev.sync;

import dev.cipherkey.core.model.SyncState;
import dev.cipherkey.core.p2p.Beacon;
import dev.cipherkey.core.p2p.Locked;
import dev.cipherkey.core.p2p.PeerService;
import dev.cipherkey.core.p2p.PeerSync;
import dev.cipherkey.core.sync.DeviceKey;
import dev.cipherkey.core.sync.ServerClient;
import dev.controller.PasswordManager;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.function.BiFunction;

/**
 * Capa de escritorio sobre {@link PeerService}: sincronización directa por red local con los dispositivos vinculados,
 * lista de dispositivos con su estado (cercano, en línea, desconectado) y vinculación/desvinculación.
 * Ver docs/adr/0001-sincronizacion-en-proximidad.md.
 */
public final class ProximityService implements Closeable {
    public enum State { NEARBY, ONLINE, OFFLINE }

    /** Fila de la lista de dispositivos. {@code address} solo si está visible en la red local. */
    public record Row(String deviceId, String name, State state, long lastSyncMillis, boolean linked, String address) {}

    /** Petición al usuario: comparar el código. Devuelve true si coincide. */
    public interface SasPrompt { boolean ask(String peerName, String sas, boolean incoming) throws Exception; }

    private static final long ONLINE_WINDOW_MS = 5 * 60_000L;

    private final PasswordManager manager;
    private final Locked locked;
    private final PeerService peers;
    private final Runnable onData;
    private final Runnable onPeerAppeared;
    private volatile List<ServerClient.DeviceInfo> serverDevices = List.of();
    private final Map<String, Long> lastOk = new HashMap<>();
    private volatile String lastError;

    public ProximityService(PasswordManager manager, int port, boolean multicast, SasPrompt prompt,
                            Runnable onData, Runnable onPeerAppeared) {
        this.manager = manager;
        this.onData = onData;
        this.onPeerAppeared = onPeerAppeared;
        this.locked = new Locked() {
            @Override public <T> T run(Work<T> work) throws Exception { return manager.withDocument(work::apply); }
        };
        this.peers = new PeerService(locked, port, multicast,
                (name, sas, incoming) -> prompt != null && prompt.ask(name, sas, incoming),
                new PeerService.Listener() {
                    @Override public void peersChanged() { ProximityService.this.onPeerAppeared.run(); }
                    @Override public void synced(PeerSync.Result r, boolean incoming) {
                        synchronized (lastOk) { lastOk.put(r.peerId(), System.currentTimeMillis()); }
                        lastError = null;
                        if (r.received() > 0 || r.newlyTrusted()) ProximityService.this.onData.run();
                    }
                    @Override public void failed(String who, String message, boolean incoming) { lastError = message; }
                }, System::currentTimeMillis);
    }

    public void start() throws Exception { peers.start(); }

    public int port() { return peers.port(); }

    public String lastError() { return lastError; }

    public void setServerDevices(List<ServerClient.DeviceInfo> devices) { this.serverDevices = List.copyOf(devices); }

    /** Permite que un dispositivo nuevo se vincule con este durante {@code millis}. */
    public void allowPairing(long millis) { peers.openPairingWindow(millis); }

    public boolean pairingOpen() { return peers.pairingOpen(); }

    // ------------------------------------------------------------------ lista

    public List<Row> rows() throws Exception {
        record Snap(byte[] key, String self, Map<String, SyncState.Peer> pinned) {}
        Snap s = locked.run(d -> d.sync == null ? null : new Snap(Base64.getDecoder().decode(d.syncKey), d.sync.deviceId, new LinkedHashMap<>(d.sync.peers)));
        if (s == null) return List.of();
        Map<String, PeerService.Nearby> byTag = new HashMap<>();
        for (PeerService.Nearby n : peers.nearby()) byTag.put(n.idTagHex(), n);

        Map<String, Row> rows = new LinkedHashMap<>();
        Set<String> usedTags = new HashSet<>();
        Map<String, ServerClient.DeviceInfo> known = new LinkedHashMap<>();
        for (ServerClient.DeviceInfo d : serverDevices) if (!d.revoked() && !d.deviceId().equals(s.self())) known.put(d.deviceId(), d);
        Set<String> ids = new LinkedHashSet<>(s.pinned().keySet());
        ids.addAll(known.keySet());
        long now = System.currentTimeMillis();
        for (String id : ids) {
            SyncState.Peer pinned = s.pinned().get(id);
            ServerClient.DeviceInfo srv = known.get(id);
            String tag = DeviceKey.hex(Beacon.idTag(s.key(), id));
            PeerService.Nearby near = byTag.get(tag);
            if (near != null) usedTags.add(tag);
            String name = pinned != null && !pinned.name.isEmpty() ? pinned.name : srv != null ? srv.name() : id;
            long last = pinned != null ? pinned.lastSync : 0;
            synchronized (lastOk) { last = Math.max(last, lastOk.getOrDefault(id, 0L)); }
            State st = near != null ? State.NEARBY : (srv != null && srv.lastSeen() > 0 && now - srv.lastSeen() < ONLINE_WINDOW_MS) ? State.ONLINE : State.OFFLINE;
            rows.put(id, new Row(id, name, st, last, pinned != null, near == null ? "" : near.address().getHostAddress() + ":" + near.port()));
        }
        // miembros de la bóveda vistos en la red que no están en ninguna lista (p. ej. sin consultar aún el servidor)
        for (var e : byTag.entrySet()) {
            if (usedTags.contains(e.getKey())) continue;
            PeerService.Nearby n = e.getValue();
            rows.put("tag:" + e.getKey(), new Row("tag:" + e.getKey(), "Dispositivo cercano", State.NEARBY, 0, false,
                    n.address().getHostAddress() + ":" + n.port()));
        }
        return new ArrayList<>(rows.values());
    }

    // ------------------------------------------------------------------ acciones

    /** Sincroniza con todos los dispositivos vinculados que estén ahora en la red local. Devuelve cuántos lo lograron. */
    public int syncTrustedNearby() {
        int ok = 0;
        try {
            for (Row r : rows()) {
                if (!r.linked() || r.state() != State.NEARBY || r.address().isEmpty()) continue;
                try {
                    peers.syncWith(parse(r.address()), false);
                    ok++;
                } catch (Exception ignored) {
                    // el error queda en lastError; el servidor sigue siendo la vía de respaldo
                }
            }
        } catch (Exception ignored) { }
        return ok;
    }

    public PeerSync.Result syncWith(String address) throws Exception {
        return peers.syncWith(parse(address), false);
    }

    /** Vincula (con confirmación del código en ambos lados) con el dispositivo en {@code address}. */
    public PeerSync.Result pairWith(String address) throws Exception {
        return peers.syncWith(parse(address), true);
    }

    public void unlink(String deviceId) throws Exception {
        locked.run(d -> { d.sync.peers.remove(deviceId); return null; });
    }

    static InetSocketAddress parse(String address) {
        String a = address.trim();
        int i = a.lastIndexOf(':');
        String host = i < 0 ? a : a.substring(0, i);
        int port = i < 0 ? PeerService.DEFAULT_PORT : Integer.parseInt(a.substring(i + 1));
        if (host.isEmpty()) throw new IllegalArgumentException("Dirección vacía");
        return new InetSocketAddress(host, port);
    }

    @Override public void close() { peers.close(); }
}
