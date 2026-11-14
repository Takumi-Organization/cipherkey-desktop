package dev.sync;

import dev.controller.PasswordManager;
import dev.model.PasswordEntry;
import dev.persistence.Vault;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Dos instalaciones de escritorio reales (PasswordManager + SyncService) sincronizando contra el binario del servidor. */
class SyncServiceE2ETest {
    static Process proc;
    static String base;
    static final char[] PWD_A = "clave-del-portatil".toCharArray();
    static final char[] PWD_B = "clave-del-sobremesa".toCharArray();
    @TempDir static Path dir;

    @BeforeAll
    static void server() throws Exception {
        String bin = System.getenv().getOrDefault("CK_SERVER_BIN",
                System.getProperty("user.home") + "/Proyectos/cipherkey-mobile/server/target/debug/cipherkey-server");
        assumeTrue(new File(bin).canExecute(), "servidor no disponible");
        int port;
        try (ServerSocket s = new ServerSocket(0)) { port = s.getLocalPort(); }
        ProcessBuilder pb = new ProcessBuilder(bin);
        pb.environment().put("CK_BIND", "127.0.0.1:" + port);
        pb.environment().put("CK_DB", dir.resolve("s.db").toString());
        pb.environment().put("CK_OPEN_REGISTRATION", "1");
        pb.redirectErrorStream(true).redirectOutput(dir.resolve("log").toFile());
        proc = pb.start();
        base = "http://127.0.0.1:" + port;
        for (int i = 0; i < 100; i++) {
            try { new java.net.URL(base + "/v1/health").openStream().close(); return; } catch (Exception e) { Thread.sleep(50); }
        }
        fail("servidor sin arrancar");
    }

    @AfterAll
    static void stop() { if (proc != null) proc.destroy(); }

    PasswordManager open(String name, char[] pwd) throws Exception {
        Path f = dir.resolve(name + ".ckey");
        Vault.create(f, pwd).close();
        PasswordManager m = new PasswordManager(f);
        m.unlock(pwd);
        return m;
    }

    PasswordEntry find(PasswordManager m, String title) {
        return m.getEntries().stream().filter(e -> title.equals(e.getTitle())).findFirst().orElse(null);
    }

    @Test
    void twoDesktopsShareOneVaultAndResolveConflicts() throws Exception {
        PasswordManager a = open("a", PWD_A), b = open("b", PWD_B);
        a.addEntry(new PasswordEntry("GitHub", "adri", "gh1", "https://github.com", ""));
        a.saveData();
        SyncService sa = new SyncService(a), sb = new SyncService(b);

        sa.enableNew(base, "", "portatil");
        sb.pair(sa.newPairingCode().encode(), "sobremesa");
        assertNotNull(find(b, "GitHub"), "A → B: la entrada creada en A aparece en B");

        // B crea y A recibe
        b.addEntry(new PasswordEntry("Banco", "u", "bk1", "", ""));
        b.saveData();
        sb.syncNow(); sa.syncNow();
        assertNotNull(find(a, "Banco"), "B → A");

        // misma entrada editada en ambos a la vez → conflicto, sin pérdida
        PasswordEntry ea = find(a, "GitHub"), eb = find(b, "GitHub");
        ea.setPassword("desde-A"); a.saveData();
        eb.setPassword("desde-B"); b.saveData();
        sa.syncNow();
        sb.syncNow();
        assertEquals(1, sb.conflicts().size());
        assertEquals("desde-B", find(b, "GitHub").getPassword());
        assertEquals("desde-A", sb.conflicts().get(0).conflict.password, "la edición ajena está conservada");

        sb.resolve(sb.conflicts().get(0).uuid, SyncService.Choice.TAKE_OTHER);
        sb.syncNow(); sa.syncNow();
        assertEquals("desde-A", find(b, "GitHub").getPassword());
        assertEquals("desde-A", find(a, "GitHub").getPassword());
        assertTrue(sb.conflicts().isEmpty());

        // borrado se propaga
        a.deleteEntry(find(a, "Banco").getId());
        a.saveData();
        sa.syncNow(); sb.syncNow();
        assertNull(find(b, "Banco"));

        // lo sincronizado sobrevive a cerrar y reabrir (cola y estado persisten cifrados)
        b.lock();
        PasswordManager b2 = new PasswordManager(dir.resolve("b.ckey"));
        b2.unlock(PWD_B);
        assertTrue(b2.isSyncConfigured());
        assertNotNull(find(b2, "GitHub"));
        assertNull(find(b2, "Banco"));
    }

    @Test
    void offlineEditsStayQueuedAndFlushLater() throws Exception {
        PasswordManager a = open("c", PWD_A);
        SyncService sa = new SyncService(a);
        sa.enableNew(base, "", "pc");
        a.addEntry(new PasswordEntry("Cola", "u", "p", "", ""));
        a.saveData();
        // el servidor «cae»: se simula apuntando la configuración a un puerto muerto
        a.withDocument(d -> { d.sync.serverUrl = "http://127.0.0.1:1"; return null; });
        assertThrows(java.io.IOException.class, sa::syncNow);
        assertEquals(1, a.pendingCount(), "la edición sigue en la cola");
        a.withDocument(d -> { d.sync.serverUrl = base; return null; });
        sa.syncNow();
        assertEquals(0, a.pendingCount());
    }

    @Test
    void revokedDesktopCannotSyncAnymore() throws Exception {
        PasswordManager a = open("d", PWD_A), b = open("e", PWD_B);
        SyncService sa = new SyncService(a), sb = new SyncService(b);
        sa.enableNew(base, "", "pc");
        sb.pair(sa.newPairingCode().encode(), "intruso");
        String bId = sb.thisDeviceId();
        sa.revoke(bId);
        b.addEntry(new PasswordEntry("X", "u", "p", "", ""));
        b.saveData();
        Exception ex = assertThrows(Exception.class, sb::syncNow);
        assertTrue(ex.getMessage().contains("403"), ex.getMessage());
        assertEquals(1, b.pendingCount(), "lo local no se pierde");
    }

    @Test
    void proximityStartFailureIsReported() throws Exception {
        // el puerto de proximidad lo ocupa este mismo proceso (o ya lo tenía otro servicio): el arranque falla
        ServerSocket hold = null;
        try { hold = new ServerSocket(dev.cipherkey.core.p2p.PeerService.DEFAULT_PORT); } catch (java.io.IOException busy) { /* ya ocupado */ }
        try {
            PasswordManager a = open("f", PWD_A);
            SyncService sa = new SyncService(a);
            sa.enableNew(base, "", "pc");
            sa.syncNow();
            if (sa.proximity() == null) {
                assertNotNull(sa.proximityError(), "el fallo de arranque no se traga en silencio");
                assertTrue(sa.proximityError().contains("sincronización directa"), sa.proximityError());
            }
        } finally {
            if (hold != null) hold.close();
        }
    }
}
