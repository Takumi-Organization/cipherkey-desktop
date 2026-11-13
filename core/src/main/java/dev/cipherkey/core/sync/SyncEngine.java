package dev.cipherkey.core.sync;

import dev.cipherkey.core.merge.Merger;
import dev.cipherkey.core.model.Record;
import dev.cipherkey.core.model.SyncState;
import dev.cipherkey.core.model.VaultDocument;
import dev.cipherkey.core.sync.Protocol.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;

/**
 * Motor de sincronización incremental y bidireccional. No es hilo-seguro: quien lo use debe impedir ediciones de la bóveda
 * mientras corre {@link #sync}. Si falla la red, el estado (cola incluida) queda intacto y se reintenta después.
 */
public final class SyncEngine {
    public record Summary(int pulled, int pushed, int conflicts, int rejected) {}

    private static final int PAGE = 200;
    private static final int MAX_ROUNDS = 5;
    private final VaultDocument doc;
    private final Transport transport;
    private final String deviceId;
    private final java.util.function.LongSupplier clock;
    private final byte[] syncKey;

    public SyncEngine(VaultDocument doc, Transport transport, java.util.function.LongSupplier clock) {
        if (doc.sync == null) throw new IllegalStateException("Esta bóveda no tiene sincronización configurada");
        this.doc = doc;
        this.transport = transport;
        this.deviceId = doc.sync.deviceId;
        this.clock = clock;
        this.syncKey = Base64.getDecoder().decode(doc.syncKey);
    }

    /** Registra que {@code r} cambió localmente y debe enviarse. Idempotente. */
    public static void markDirty(VaultDocument doc, Record r) {
        if (doc.sync != null) doc.sync.pending.put(r.uuid, "");
    }

    public Summary sync() throws IOException, GeneralSecurityException {
        int pulled = 0, pushed = 0, rejected = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            pulled += pullAll();
            PushOutcome out = pushPending();
            pushed += out.pushed;
            rejected += out.rejected;
            if (!out.retry) break;
        }
        int conflicts = (int) doc.records.stream().filter(r -> r.conflict != null).count();
        return new Summary(pulled, pushed, conflicts, rejected);
    }

    private int pullAll() throws IOException, GeneralSecurityException {
        SyncState st = doc.sync;
        int count = 0;
        while (true) {
            PullResponse resp = transport.pull(st.lastSeq, PAGE);
            for (Change c : resp.changes()) {
                apply(c);
                count++;
                st.lastSeq = Math.max(st.lastSeq, c.seq());
            }
            if (!resp.more() || resp.changes().isEmpty()) {
                if (!resp.more()) st.lastSeq = Math.max(st.lastSeq, resp.latestSeq());
                return count;
            }
        }
    }

    /** Integra un cambio remoto en la bóveda local. */
    private void apply(Change c) throws GeneralSecurityException {
        SyncState st = doc.sync;
        SyncState.Baseline known = st.baselines.get(c.uuid());
        if (known != null && known.ver() >= c.ver()) return; // ya lo teníamos (p. ej. nuestro propio envío)
        Record remote = Envelope.open(syncKey, doc.vaultId, c.uuid(), c.blob());
        remote.deleted = c.deleted() || remote.deleted;
        Optional<Record> localOpt = doc.find(c.uuid());
        if (localOpt.isEmpty()) {
            doc.records.add(remote);
            st.baselines.put(c.uuid(), new SyncState.Baseline(c.ver(), remote.copy()));
            return;
        }
        Record local = localOpt.get();
        boolean dirty = st.pending.containsKey(c.uuid());
        if (!dirty && local.conflict == null) {
            replace(local, remote);
        } else {
            Record base = known != null ? known.base() : null;
            Merger.Result res = Merger.merge(base, local, remote, deviceId, clock.getAsLong());
            replace(local, res.record());
            if (res.needsPush() && !res.hasConflict()) st.pending.put(c.uuid(), "");
            else st.pending.remove(c.uuid());
            if (res.hasConflict() && res.record().conflict != null) {
                // se retiene el envío hasta que el usuario resuelva; la base pasa a ser la versión remota
                st.pending.remove(c.uuid());
            }
        }
        st.baselines.put(c.uuid(), new SyncState.Baseline(c.ver(), remote.copy()));
    }

    private void replace(Record target, Record src) {
        int i = doc.records.indexOf(target);
        doc.records.set(i, src);
    }

    private record PushOutcome(int pushed, int rejected, boolean retry) {}

    private PushOutcome pushPending() throws IOException, GeneralSecurityException {
        SyncState st = doc.sync;
        List<PushOp> ops = new ArrayList<>();
        Map<String, Record> sent = new HashMap<>();
        for (String uuid : new ArrayList<>(st.pending.keySet())) {
            Optional<Record> r = doc.find(uuid);
            if (r.isEmpty() || r.get().conflict != null) { st.pending.remove(uuid); continue; }
            Record rec = r.get();
            SyncState.Baseline b = st.baselines.get(uuid);
            long baseVer = b == null ? 0 : b.ver();
            String opId = uuid + ":" + rec.rev + ":" + baseVer;
            ops.add(new PushOp(opId, uuid, baseVer, rec.deleted, Envelope.seal(syncKey, doc.vaultId, rec)));
            sent.put(opId, rec.copy());
        }
        if (ops.isEmpty()) return new PushOutcome(0, 0, false);
        int pushed = 0, rejected = 0;
        boolean retry = false;
        for (PushResult res : transport.push(ops)) {
            Record snapshot = sent.get(res.opId());
            if (snapshot == null) continue;
            switch (res.status()) {
                case "ok" -> {
                    pushed++;
                    st.baselines.put(snapshot.uuid, new SyncState.Baseline(res.ver(), snapshot));
                    Optional<Record> cur = doc.find(snapshot.uuid);
                    if (cur.isPresent() && cur.get().rev == snapshot.rev) st.pending.remove(snapshot.uuid);
                }
                case "conflict" -> {
                    if (res.current() != null) { apply(res.current()); retry = true; }
                }
                default -> rejected++; // se conserva en la cola: nunca se pierde una edición
            }
        }
        return new PushOutcome(pushed, rejected, retry);
    }
}
