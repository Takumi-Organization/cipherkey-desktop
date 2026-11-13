package dev.sync;

import dev.cipherkey.core.merge.Merger;
import dev.cipherkey.core.model.Record;
import dev.cipherkey.core.model.SyncState;
import dev.cipherkey.core.model.VaultDocument;
import dev.cipherkey.core.sync.*;
import dev.controller.PasswordManager;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Sincronización opcional del escritorio: ejecuta {@link SyncEngine} en segundo plano cada minuto y tras cada cambio local
 * (con un pequeño retardo para agrupar ediciones). Sin red no falla: lo pendiente queda en la cola de la bóveda.
 */
public final class SyncService {
    public record Status(boolean configured, boolean running, long lastSyncMillis, int pending, int conflicts, String error) {}

    public enum Choice { KEEP_MINE, TAKE_OTHER, KEEP_BOTH }

    private static final long PERIOD_SECONDS = 60;
    private static final long DEBOUNCE_MILLIS = 1500;

    private final PasswordManager manager;
    private final ScheduledExecutorService pool = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cipherkey-sync");
        t.setDaemon(true);
        return t;
    });
    private volatile Consumer<Status> listener = s -> { };
    private volatile long lastSync;
    private volatile String lastError;
    private volatile boolean running;
    private ScheduledFuture<?> debounce;

    public SyncService(PasswordManager manager) {
        this.manager = manager;
    }

    /** {@code listener} se invoca desde el hilo de sincronización: la interfaz debe pasar al hilo de Swing. */
    public void start(Consumer<Status> listener) {
        this.listener = listener;
        pool.scheduleWithFixedDelay(this::runQuietly, 5, PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    public void stop() {
        pool.shutdownNow();
    }

    /** Pide una sincronización pronto (agrupando llamadas seguidas). */
    public synchronized void syncSoon() {
        if (!manager.isSyncConfigured() || pool.isShutdown()) return;
        if (debounce != null) debounce.cancel(false);
        debounce = pool.schedule(this::runQuietly, DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
    }

    public Status status() {
        return new Status(manager.isSyncConfigured(), running, lastSync, manager.pendingCount(), manager.conflicts().size(), lastError);
    }

    private void runQuietly() {
        try {
            syncNow();
        } catch (Exception ignored) {
            // el error queda en lastError y se notifica; la cola se conserva para el siguiente intento
        }
    }

    /** Sincroniza ahora (bloqueante). Devuelve el resumen o lanza la causa del fallo. */
    public SyncEngine.Summary syncNow() throws Exception {
        if (!manager.isSyncConfigured()) throw new IllegalStateException("La sincronización no está configurada");
        running = true;
        listener.accept(status());
        try {
            SyncEngine.Summary s = manager.withDocument(d -> {
                ServerClient c = new ServerClient(d.sync.serverUrl, d.sync.deviceId, DeviceKey.fromPrivateBase64(d.sync.devicePrivateKey));
                return new SyncEngine(d, c, System::currentTimeMillis).sync();
            });
            lastSync = System.currentTimeMillis();
            lastError = null;
            return s;
        } catch (Exception e) {
            lastError = e instanceof IOException ? "Sin conexión con el servidor (" + e.getMessage() + ")" : String.valueOf(e.getMessage());
            throw e;
        } finally {
            running = false;
            listener.accept(status());
        }
    }

    // ------------------------------------------------------------------ configuración

    /** Crea la bóveda en el servidor con este dispositivo como primero y encola todo para subirlo. */
    public void enableNew(String serverUrl, String adminToken, String deviceName) throws Exception {
        manager.withDocument(d -> {
            DeviceKey key = DeviceKey.generate();
            String deviceId = ServerClient.createVault(serverUrl, adminToken, d.vaultId, key, deviceName);
            attach(d, serverUrl, deviceId, deviceName, key);
            return null;
        });
        syncNow();
    }

    /** Vincula este dispositivo a una bóveda ya sincronizada con el código del otro dispositivo. */
    public void pair(String codeText, String deviceName) throws Exception {
        PairingCode code = PairingCode.decode(codeText);
        manager.withDocument(d -> {
            DeviceKey key = DeviceKey.generate();
            String deviceId = ServerClient.enroll(code.server(), code.vaultId(), code.token(), key, deviceName);
            d.vaultId = code.vaultId();
            d.syncKey = code.syncKey();
            attach(d, code.server(), deviceId, deviceName, key);
            return null;
        });
        syncNow();
    }

    private static void attach(VaultDocument d, String url, String deviceId, String name, DeviceKey key) {
        SyncState s = new SyncState();
        s.serverUrl = url; s.deviceId = deviceId; s.deviceName = name; s.devicePrivateKey = key.privateBase64();
        d.sync = s;
        for (Record r : d.records) SyncEngine.markDirty(d, r); // subida inicial
    }

    /** Código para vincular otro dispositivo (caduca en 10 minutos y sirve una vez). Contiene la clave de sincronización. */
    public PairingCode newPairingCode() throws Exception {
        return manager.withDocument(d -> {
            ServerClient c = client(d);
            return new PairingCode(d.sync.serverUrl, d.vaultId, d.syncKey, c.newEnrollment().token());
        });
    }

    public List<ServerClient.DeviceInfo> devices() throws Exception {
        return manager.withDocument(d -> client(d).devices());
    }

    public String thisDeviceId() throws Exception {
        return manager.withDocument(d -> d.sync == null ? "" : d.sync.deviceId);
    }

    public void revoke(String deviceId) throws Exception {
        manager.withDocument(d -> { client(d).revoke(deviceId); return null; });
    }

    /** Desactiva la sincronización en este dispositivo; los datos locales se conservan. No borra nada del servidor. */
    public void disable() throws Exception {
        manager.withDocument(d -> { d.sync = null; return null; });
        listener.accept(status());
    }

    private static ServerClient client(VaultDocument d) {
        if (d.sync == null) throw new IllegalStateException("La sincronización no está configurada");
        return new ServerClient(d.sync.serverUrl, d.sync.deviceId, DeviceKey.fromPrivateBase64(d.sync.devicePrivateKey));
    }

    // ------------------------------------------------------------------ conflictos

    public List<Record> conflicts() {
        return manager.conflicts();
    }

    public void resolve(String uuid, Choice choice) throws Exception {
        manager.withDocument(d -> {
            Record r = d.find(uuid).orElseThrow(() -> new IllegalArgumentException("Registro inexistente"));
            String dev = d.sync != null ? d.sync.deviceId : "local";
            long now = System.currentTimeMillis();
            int i = d.records.indexOf(r);
            switch (choice) {
                case KEEP_MINE, TAKE_OTHER -> {
                    Record out = Merger.resolve(r, choice == Choice.TAKE_OTHER, dev, now);
                    d.records.set(i, out);
                    SyncEngine.markDirty(d, out);
                }
                case KEEP_BOTH -> {
                    Record copy = Merger.keepBoth(r, dev, now);
                    d.records.add(copy);
                    SyncEngine.markDirty(d, copy);
                    Record mine = Merger.resolve(r, false, dev, now);
                    d.records.set(i, mine);
                    SyncEngine.markDirty(d, mine);
                }
            }
            return null;
        });
        syncSoon();
    }

}
