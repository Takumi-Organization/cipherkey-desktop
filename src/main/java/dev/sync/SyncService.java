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
    private volatile String proximityError;
    private volatile boolean running;
    private ScheduledFuture<?> debounce;
    private volatile ProximityService proximity;
    private volatile ProximityService.SasPrompt sasPrompt = (n, c, i) -> false;
    private volatile Runnable dataListener = () -> { };
    private volatile String lastRoute = "";
    private volatile List<ServerClient.DeviceInfo> cachedDevices = List.of();
    private volatile long devicesAt;

    public SyncService(PasswordManager manager) {
        this.manager = manager;
    }

    /** {@code listener} se invoca desde el hilo de sincronización: la interfaz debe pasar al hilo de Swing. */
    public void start(Consumer<Status> listener) {
        this.listener = listener;
        pool.schedule(this::ensureProximity, 1, TimeUnit.SECONDS);
        pool.scheduleWithFixedDelay(this::runQuietly, 5, PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    public void stop() {
        pool.shutdownNow();
        ProximityService p = proximity;
        if (p != null) p.close();
    }

    /** Quién muestra el código de seguridad al vincular por proximidad (interfaz de usuario). */
    public void setSasPrompt(ProximityService.SasPrompt prompt) { this.sasPrompt = prompt; }

    /** Se invoca (en un hilo de fondo) cuando una sincronización directa cambió datos locales. */
    public void setDataListener(Runnable r) { this.dataListener = r; }

    /** Servicio de proximidad; se crea al estar configurada la sincronización. Puede ser {@code null}. */
    public ProximityService proximity() { return proximity; }

    public String lastRoute() { return lastRoute; }

    /** Motivo por el que no arrancó la sincronización directa (puerto ocupado, etc.), o {@code null}. */
    public String proximityError() { return proximityError; }

    private synchronized void ensureProximity() {
        if (proximity != null || !manager.isSyncConfigured() || pool.isShutdown()) return;
        try {
            ProximityService p = new ProximityService(manager, dev.cipherkey.core.p2p.PeerService.DEFAULT_PORT, true,
                    (name, sas, incoming) -> sasPrompt.ask(name, sas, incoming),
                    () -> { dataListener.run(); syncSoonAfterPeer(); }, this::syncSoon);
            p.start();
            proximity = p;
            proximityError = null;
        } catch (Exception e) {
            // puerto ocupado u otro fallo: la sincronización por servidor sigue funcionando
            proximityError = "No se pudo iniciar la sincronización directa: " + e;
        }
    }

    private void syncSoonAfterPeer() { syncSoon(); }

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
        ensureProximity();
        // 1) Proximidad primero: si hay dispositivos vinculados en la red local, se sincroniza directamente con ellos.
        int viaPeers = 0;
        ProximityService prox = proximity;
        if (prox != null) {
            try { prox.setServerDevices(cachedDevices); } catch (RuntimeException ignored) { }
            viaPeers = prox.syncTrustedNearby();
        }
        try {
            // 2) Servidor: siempre que esté accesible (orden global y dispositivos que no están cerca).
            SyncEngine.Summary s = manager.withDocument(d -> {
                ServerClient c = new ServerClient(d.sync.serverUrl, d.sync.deviceId, DeviceKey.fromPrivateBase64(d.sync.devicePrivateKey));
                return new SyncEngine(d, c, System::currentTimeMillis).sync();
            });
            lastSync = System.currentTimeMillis();
            lastError = null;
            lastRoute = viaPeers > 0 ? "proximidad + servidor" : "servidor";
            return s;
        } catch (Exception e) {
            if (viaPeers > 0) {
                // 3) Servidor inaccesible pero sincronizado por proximidad: no es un fallo para el usuario.
                lastSync = System.currentTimeMillis();
                lastError = null;
                lastRoute = "proximidad (servidor no accesible)";
                return new SyncEngine.Summary(0, 0, manager.conflicts().size(), 0);
            }
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
        List<ServerClient.DeviceInfo> list = manager.withDocument(d -> client(d).devices());
        cachedDevices = list;
        devicesAt = System.currentTimeMillis();
        return list;
    }

    /** Última lista de dispositivos del servidor sin volver a preguntar (puede estar vacía si nunca se consultó). */
    public List<ServerClient.DeviceInfo> cachedDevices() { return cachedDevices; }

    public long devicesAge() { return devicesAt == 0 ? Long.MAX_VALUE : System.currentTimeMillis() - devicesAt; }

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
