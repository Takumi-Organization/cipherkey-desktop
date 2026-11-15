package dev.controller;

import com.google.gson.JsonParseException;
import dev.cipherkey.core.model.Record;
import dev.cipherkey.core.model.V2Migrator;
import dev.cipherkey.core.model.VaultDocument;
import dev.cipherkey.core.sync.SyncEngine;
import dev.model.PasswordEntry;
import dev.model.PasswordHistoryItem;
import dev.persistence.Vault;
import dev.persistence.VaultException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Sesión de una bóveda: mantiene las entradas descifradas y la clave derivada (dentro de {@link Vault}).
 * No conserva la contraseña maestra tras {@link #unlock}.
 *
 * <p>La interfaz trabaja con {@link PasswordEntry}; por debajo la bóveda es un {@link VaultDocument} v3 con registros
 * sincronizables. {@link #saveData} reconcilia ambos (altas, cambios y borrados) y marca lo cambiado para sincronizar.
 * Los métodos son {@code synchronized}: la sincronización en segundo plano y la interfaz comparten la sesión.
 */
public class PasswordManager {
    private static final int MAX_HISTORY = 10;
    private static final String LOCAL_DEVICE = "local";

    @FunctionalInterface
    public interface DocAction<T> {
        T run(VaultDocument doc) throws Exception;
    }

    private final Path vaultFile;
    private Vault vault;
    private VaultDocument doc;
    private List<PasswordEntry> entries = new ArrayList<>();
    private int nextId = 1;

    public PasswordManager(Path vaultFile) {
        this.vaultFile = vaultFile;
    }

    /** Abre la bóveda y, si es v2, la migra a v3 conservando {@code .v2.bak}. La contraseña no se modifica. */
    public synchronized void unlock(char[] password) throws VaultException {
        Vault opened = Vault.open(vaultFile, password);
        try {
            byte[] data = opened.data();
            String text = new String(data, StandardCharsets.UTF_8).trim();
            VaultDocument loaded;
            if (text.startsWith("[")) { // contenido v2 (o bóveda recién creada)
                loaded = V2Migrator.migrate(data, ZoneId.systemDefault(), LOCAL_DEVICE);
                if (opened.version() == 2) opened.upgradeToV3(loaded.toBytes());
                else opened.save(loaded.toBytes());
            } else {
                loaded = VaultDocument.fromBytes(data);
            }
            doc = loaded;
        } catch (JsonParseException | IllegalStateException | IllegalArgumentException e) {
            opened.close();
            throw new VaultException("El contenido de la bóveda no es válido: " + e.getMessage(), e);
        } catch (VaultException e) {
            opened.close();
            throw e;
        }
        vault = opened;
        entries = new ArrayList<>();
        nextId = 1;
        rebuildEntries();
    }

    public synchronized void saveData() throws VaultException {
        requireSession();
        reconcile();
        vault.save(doc.toBytes());
    }

    public synchronized void lock() {
        if (vault != null) {
            vault.close();
            vault = null;
        }
        doc = null;
        entries.clear();
    }

    /** Cambia la clave maestra (nueva sal incluida). Comprueba antes la actual. */
    public synchronized void changeMasterPassword(char[] oldPassword, char[] newPassword) throws VaultException {
        requireSession();
        Vault.open(vaultFile, oldPassword).close(); // lanza InvalidPassword si la actual no es correcta
        reconcile();
        vault.changePassword(newPassword, doc.toBytes());
    }

    /** Copia cifrada de la bóveda SIN la identidad de sincronización de este dispositivo. */
    public synchronized void exportEncryptedCopy(Path target) throws VaultException {
        requireSession();
        reconcile();
        vault.exportCopy(target, doc.withoutSync().toBytes());
    }

    public synchronized List<PasswordEntry> getEntries() {
        return entries;
    }

    public synchronized void addEntry(PasswordEntry entry) {
        entry.setId(nextId++);
        entries.add(entry);
    }

    /** Sustituye la entrada con el mismo id. Si cambia la contraseña, la anterior pasa al historial. */
    public synchronized void updateEntry(PasswordEntry updated) {
        for (int i = 0; i < entries.size(); i++) {
            PasswordEntry previous = entries.get(i);
            if (previous.getId() != updated.getId()) continue;

            updated.setUuid(previous.getUuid());
            List<PasswordHistoryItem> history = new ArrayList<>(previous.getHistory());
            String oldPwd = previous.getPassword();
            if (oldPwd != null && !oldPwd.isEmpty() && !oldPwd.equals(updated.getPassword())) {
                history.add(0, new PasswordHistoryItem(oldPwd, LocalDateTime.now()));
            }
            updated.setHistory(history.subList(0, Math.min(MAX_HISTORY, history.size())));
            entries.set(i, updated);
            return;
        }
    }

    /**
     * Añade las entradas que no estén ya (mismo nombre, usuario y sitio, sin distinguir mayúsculas).
     *
     * @return cuántas se añadieron
     */
    public synchronized int importEntries(List<PasswordEntry> incoming) {
        int added = 0;
        for (PasswordEntry e : incoming) {
            boolean duplicate = entries.stream().anyMatch(x -> identity(x).equals(identity(e)));
            if (!duplicate) {
                addEntry(e);
                added++;
            }
        }
        return added;
    }

    private static List<String> identity(PasswordEntry e) {
        return java.util.stream.Stream.of(e.getTitle(), e.getUsername(), e.getUrl())
                .map(s -> Objects.toString(s, "").trim().toLowerCase(Locale.ROOT)).toList();
    }

    public Path getVaultFile() {
        return vaultFile;
    }

    public synchronized void deleteEntry(int id) {
        entries.removeIf(e -> e.getId() == id);
    }

    // ---------------------------------------------------------------- sincronización

    /**
     * Ejecuta una acción sobre el documento (p. ej. una sincronización) con la sesión bloqueada: antes reconcilia los
     * cambios de la interfaz, después reconstruye las entradas y guarda, incluso si la acción falla a medias.
     */
    public synchronized <T> T withDocument(DocAction<T> action) throws Exception {
        requireSession();
        reconcile();
        try {
            return action.run(doc);
        } finally {
            rebuildEntries();
            vault.save(doc.toBytes());
        }
    }

    public synchronized boolean isUnlocked() {
        return doc != null && vault != null;
    }

    /**
     * Copia de seguridad local de la bóveda (el mismo fichero cifrado, con la misma clave maestra) junto al original, como
     * {@code <bóveda>.bak-proximidad-<fecha>}. Se usa antes de la primera fusión con un dispositivo nuevo. Devuelve la ruta.
     */
    public synchronized Path backupVault(String tag) throws Exception {
        requireSession();
        reconcile();
        vault.save(doc.toBytes()); // lo último de la interfaz, en disco antes de copiar
        String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        Path target = vaultFile.resolveSibling(vaultFile.getFileName() + ".bak-" + tag + "-" + stamp);
        java.nio.file.Files.copy(vaultFile, target, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
        try {
            java.nio.file.Files.setPosixFilePermissions(target, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) { /* sistemas sin POSIX */ }
        return target;
    }

    public synchronized boolean isSyncConfigured() {
        return doc != null && doc.sync != null && !doc.sync.serverUrl.isEmpty();
    }

    /** Registros con conflicto pendiente (copias; para resolver, usar {@link #withDocument}). */
    public synchronized List<Record> conflicts() {
        if (doc == null) return List.of();
        return doc.records.stream().filter(r -> r.conflict != null).map(Record::copy).toList();
    }

    public synchronized int pendingCount() {
        return doc == null || doc.sync == null ? 0 : doc.sync.pending.size();
    }

    // ---------------------------------------------------------------- conversión entradas <-> registros

    private String deviceId() {
        return doc.sync != null && !doc.sync.deviceId.isEmpty() ? doc.sync.deviceId : LOCAL_DEVICE;
    }

    private void rebuildEntries() {
        Map<String, Integer> ids = new HashMap<>();
        for (PasswordEntry e : entries) ids.put(e.getUuid(), e.getId());
        List<PasswordEntry> fresh = new ArrayList<>();
        for (Record r : doc.records) {
            if (r.deleted) continue;
            PasswordEntry e = toEntry(r);
            Integer id = ids.get(r.uuid);
            e.setId(id != null ? id : nextId++);
            fresh.add(e);
        }
        entries.clear();
        entries.addAll(fresh);
    }

    /** Vuelca los cambios de la interfaz en los registros y los marca para sincronizar. */
    private void reconcile() {
        long now = System.currentTimeMillis();
        Set<String> present = new HashSet<>();
        for (PasswordEntry e : entries) {
            present.add(e.getUuid());
            Optional<Record> found = doc.find(e.getUuid());
            if (found.isEmpty()) {
                Record r = Record.create(deviceId(), now);
                r.uuid = e.getUuid();
                r.created = millis(e.getCreated(), now);
                copyInto(e, r);
                doc.records.add(r);
                SyncEngine.markDirty(doc, r);
            } else if (differs(found.get(), e)) {
                Record r = found.get();
                copyInto(e, r);
                r.touch(deviceId(), now);
                SyncEngine.markDirty(doc, r);
            }
        }
        for (Record r : doc.records) {
            if (!r.deleted && !present.contains(r.uuid)) {
                r.markDeleted(deviceId(), now);
                SyncEngine.markDirty(doc, r);
            }
        }
    }

    private static boolean differs(Record r, PasswordEntry e) {
        return !n(r.title).equals(n(e.getTitle())) || !n(r.username).equals(n(e.getUsername()))
                || !n(r.password).equals(n(e.getPassword())) || !n(r.url).equals(n(e.getUrl()))
                || !n(r.notes).equals(n(e.getNotes())) || !n(r.category).equals(n(e.getCategory()))
                || !n(r.totpSecret).equals(n(e.getTotpSecret())) || r.favorite != e.isFavorite()
                || !r.history.equals(historyOf(e));
    }

    private static void copyInto(PasswordEntry e, Record r) {
        r.title = n(e.getTitle()); r.username = n(e.getUsername()); r.password = n(e.getPassword());
        r.url = n(e.getUrl()); r.notes = n(e.getNotes()); r.category = n(e.getCategory());
        r.totpSecret = n(e.getTotpSecret()); r.favorite = e.isFavorite();
        r.history = new ArrayList<>(historyOf(e));
    }

    private static List<Record.HistoryItem> historyOf(PasswordEntry e) {
        return e.getHistory().stream()
                .map(h -> new Record.HistoryItem(n(h.getPassword()), millis(h.getReplacedAt(), 0)))
                .toList();
    }

    private static PasswordEntry toEntry(Record r) {
        PasswordEntry e = new PasswordEntry();
        e.setUuid(r.uuid);
        e.setTitle(r.title); e.setUsername(r.username); e.setPassword(r.password);
        e.setUrl(r.url); e.setNotes(r.notes); e.setCategory(r.category.isEmpty() ? null : r.category);
        e.setTotpSecret(r.totpSecret.isEmpty() ? null : r.totpSecret);
        e.setFavorite(r.favorite);
        e.setHistory(r.history.stream().map(h -> new PasswordHistoryItem(h.password(), ldt(h.replacedAt()))).toList());
        e.setCreated(ldt(r.created));
        e.setModified(ldt(r.updatedAt));
        return e;
    }

    private static String n(String s) {
        return s == null ? "" : s;
    }

    private static long millis(LocalDateTime t, long fallback) {
        return t == null ? fallback : t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static LocalDateTime ldt(long ms) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
    }

    private void requireSession() {
        if (vault == null) {
            throw new IllegalStateException("No hay sesión activa");
        }
    }
}
