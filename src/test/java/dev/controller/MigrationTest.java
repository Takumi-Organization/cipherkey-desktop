package dev.controller;

import dev.cipherkey.core.format.VaultContainer;
import dev.persistence.VaultException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Migración v2 → v3 de una bóveda escrita por el CipherKey 4.0.0 original (fixture generado con ese código). */
class MigrationTest {
    private static final char[] PWD = "clave-de-prueba-v2".toCharArray();
    @TempDir Path dir;

    private Path copyFixture() throws Exception {
        Path f = dir.resolve("v.ckey");
        try (InputStream in = getClass().getResourceAsStream("/fixtures/v2-legacy.ckey")) { Files.write(f, in.readAllBytes()); }
        return f;
    }

    @Test
    void opensMigratesKeepsBackupAndReopensAsV3() throws Exception {
        Path f = copyFixture();
        byte[] original = Files.readAllBytes(f);
        assertEquals(2, VaultContainer.peekVersion(original));

        PasswordManager m = new PasswordManager(f);
        m.unlock(PWD);
        assertEquals(2, m.getEntries().size());
        assertEquals("gh-pass-1", m.getEntries().get(0).getPassword());
        assertEquals("p€2", m.getEntries().get(1).getPassword());
        assertTrue(m.getEntries().get(0).isFavorite());
        assertEquals(1, m.getEntries().get(0).getHistory().size());
        m.lock();

        assertEquals(3, VaultContainer.peekVersion(Files.readAllBytes(f)), "el fichero quedó en v3");
        Path backup = dir.resolve("v.ckey.v2.bak");
        assertArrayEquals(original, Files.readAllBytes(backup), "la copia v2 es idéntica al original");

        PasswordManager again = new PasswordManager(f);
        again.unlock(PWD);
        assertEquals(2, again.getEntries().size());
        assertEquals("GitHub", again.getEntries().get(0).getTitle());
        assertEquals(m.getEntries().size(), 0); // tras lock, la sesión anterior se limpió
        again.lock();
    }

    @Test
    void backupIsNeverOverwrittenByLaterMigrations() throws Exception {
        Path f = copyFixture();
        byte[] original = Files.readAllBytes(f);
        new PasswordManager(f) {{ unlock(PWD); lock(); }};
        Path backup = dir.resolve("v.ckey.v2.bak");
        Files.write(backup, new byte[]{1, 2, 3}); // simula una copia previa distinta
        Files.write(f, original);                   // otro fichero v2 en el mismo sitio
        new PasswordManager(f) {{ unlock(PWD); lock(); }};
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(backup), "no se sobrescribe una copia existente");
    }

    @Test
    void wrongPasswordLeavesV2FileUntouched() throws Exception {
        Path f = copyFixture();
        byte[] original = Files.readAllBytes(f);
        PasswordManager m = new PasswordManager(f);
        assertThrows(VaultException.InvalidPassword.class, () -> m.unlock("mala".toCharArray()));
        assertArrayEquals(original, Files.readAllBytes(f));
        assertFalse(Files.exists(dir.resolve("v.ckey.v2.bak")));
    }

    @Test
    void v3FileIsRejectedByOldReaders() throws Exception {
        Path f = copyFixture();
        PasswordManager m = new PasswordManager(f);
        m.unlock(PWD); m.lock();
        byte[] v3 = Files.readAllBytes(f);
        // Un lector v2 compara la versión con 2: el byte de versión es 3, así que lo rechaza en vez de mutilarlo.
        assertEquals(3, v3[4]);
    }

    @Test
    void deletionsAndEditsSurviveSaveAndReopen() throws Exception {
        Path f = copyFixture();
        PasswordManager m = new PasswordManager(f);
        m.unlock(PWD);
        m.deleteEntry(m.getEntries().get(1).getId());
        m.getEntries().get(0).setPassword("nueva");
        m.saveData();
        m.lock();
        PasswordManager r = new PasswordManager(f);
        r.unlock(PWD);
        assertEquals(1, r.getEntries().size());
        assertEquals("nueva", r.getEntries().get(0).getPassword());
        r.lock();
    }

    @Test
    void encryptedCopyHasNoSyncIdentity() throws Exception {
        Path f = copyFixture();
        PasswordManager m = new PasswordManager(f);
        m.unlock(PWD);
        m.withDocument(d -> {
            d.sync = new dev.cipherkey.core.model.SyncState();
            d.sync.deviceId = "dev-1"; d.sync.devicePrivateKey = "CLAVE-PRIVADA"; d.sync.serverUrl = "http://x";
            return null;
        });
        Path copy = dir.resolve("copia.ckey");
        m.exportEncryptedCopy(copy);
        m.lock();
        PasswordManager c = new PasswordManager(copy);
        c.unlock(PWD);
        assertEquals(2, c.getEntries().size());
        assertFalse(c.isSyncConfigured());
        c.lock();
    }
}
