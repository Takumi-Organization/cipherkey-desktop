package dev.sync;

import dev.controller.PasswordManager;
import dev.model.PasswordEntry;
import dev.persistence.Vault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proximidad SIN servidor de sincronización, con dos instalaciones de escritorio reales (PasswordManager + SyncService +
 * ProximityService, bóvedas temporales con claves de prueba, sockets reales en loopback). Sin red multidifusión ni servidor.
 */
class ProximityNoServerE2ETest {
    @TempDir Path dir;

    /** Un «dispositivo»: su bóveda, su servicio y las respuestas simuladas del usuario. */
    final class Dev implements AutoCloseable {
        final PasswordManager manager;
        final SyncService service;
        final List<String> sas = Collections.synchronizedList(new ArrayList<>());
        final List<String> summaries = Collections.synchronizedList(new ArrayList<>());
        volatile boolean acceptSas = true, acceptMerge = true;

        Dev(String name) throws Exception {
            char[] pwd = ("clave-de-prueba-" + name).toCharArray();
            Path f = dir.resolve(name + ".ckey");
            Vault.create(f, pwd).close();
            manager = new PasswordManager(f);
            manager.unlock(pwd);
            service = new SyncService(manager);
            service.setProximityPort(0);
            service.setProximityMulticast(false);
            service.setSasPrompt((peer, code, incoming) -> { sas.add(code); return acceptSas; });
            service.setMergePrompt(x -> { summaries.add(x.mineOnly() + "/" + x.theirsOnly() + "/" + x.differing()); return acceptMerge; });
            assertNull(service.retryProximity(), "la proximidad debe arrancar sin servidor");
            assertNotNull(service.proximity());
            assertFalse(manager.isSyncConfigured(), "sin servidor configurado");
        }

        void add(String title, String pass) throws Exception {
            manager.addEntry(new PasswordEntry(title, "u-" + title, pass, "", ""));
            manager.saveData();
        }

        PasswordEntry find(String title) {
            return manager.getEntries().stream().filter(e -> title.equals(e.getTitle())).findFirst().orElse(null);
        }

        String addr() { return "127.0.0.1:" + service.proximity().port(); }

        long backups() throws Exception {
            try (Stream<Path> s = Files.list(dir)) {
                return s.filter(p -> p.getFileName().toString().startsWith(manager.getVaultFile().getFileName() + ".bak-proximidad-")).count();
            }
        }

        @Override public void close() { service.stop(); manager.lock(); }
    }

    @Test
    void dosBovedasDistintasSeUnenSinServidorYSinPerderNada() throws Exception {
        try (Dev a = new Dev("a"); Dev b = new Dev("b")) {
            a.add("GitHub", "gh-de-a"); a.add("Banco", "bk");
            b.add("Correo", "co"); b.add("GitHub", "gh-de-b"); // otro registro con el mismo título: son entradas distintas

            b.service.proximity().allowPairing(60_000);
            var r = a.service.proximity().pairWith(b.addr());
            assertTrue(r.newlyTrusted() && r.firstMerge());
            assertEquals(1, a.sas.size());
            assertEquals(a.sas.get(0), b.sas.get(0), "mismo código en las dos pantallas");
            // resumen: A aporta 2, B aporta 2
            assertEquals("2/2/0", a.summaries.get(0));
            assertEquals("2/2/0", b.summaries.get(0));
            assertEquals(1, a.backups()); assertEquals(1, b.backups());
            for (Dev d : List.of(a, b)) {
                assertEquals(4, d.manager.getEntries().size(), "unión: ninguna entrada se pierde ni se sustituye");
                assertNotNull(d.find("Banco")); assertNotNull(d.find("Correo"));
            }
            assertEquals("gh-de-a", a.find("GitHub").getPassword());
            assertEquals("bk", b.find("Banco").getPassword());

            // la bóveda sigue sin servidor, y la identidad/vínculo sobreviven a cerrar y reabrir
            assertFalse(a.manager.isSyncConfigured());
            // segunda sincronización: sin código ni resumen
            a.add("Nueva", "n");
            a.service.proximity().syncWith(b.addr());
            assertNotNull(b.find("Nueva"));
            assertEquals(1, a.sas.size());
            assertEquals(1, a.summaries.size());
        }
    }

    @Test
    void sasRechazadoNoVinculaNiIntercambiaNada() throws Exception {
        try (Dev a = new Dev("a"); Dev b = new Dev("b")) {
            a.add("Solo-A", "x");
            b.acceptSas = false;
            b.service.proximity().allowPairing(60_000);
            assertThrows(Exception.class, () -> a.service.proximity().pairWith(b.addr()));
            assertNull(b.find("Solo-A"));
            assertTrue(b.summaries.isEmpty() && a.summaries.isEmpty(), "no se llega al resumen sin código confirmado");
            assertTrue(a.service.proximity().rows().stream().noneMatch(ProximityService.Row::linked));
            assertEquals(0, a.backups());
        }
    }

    @Test
    void resumenRechazadoNoCambiaNadaYDesvincularCortaLaSincronizacion() throws Exception {
        try (Dev a = new Dev("a"); Dev b = new Dev("b")) {
            a.add("Solo-A", "x"); b.add("Solo-B", "y");
            a.acceptMerge = false;
            b.service.proximity().allowPairing(60_000);
            assertThrows(Exception.class, () -> a.service.proximity().pairWith(b.addr()));
            assertNull(a.find("Solo-B")); assertNull(b.find("Solo-A"));

            // aceptar ahora: el vínculo (SAS) ya existía y la unión pasa a hacerse
            a.acceptMerge = true;
            a.service.proximity().syncWith(b.addr());
            assertNotNull(a.find("Solo-B")); assertNotNull(b.find("Solo-A"));

            // desvincular en B: A ya no puede sincronizar con B
            String aId = b.service.proximity().rows().stream().filter(ProximityService.Row::linked).findFirst().orElseThrow().deviceId();
            b.service.proximity().unlink(aId);
            a.add("Despues", "z");
            assertThrows(Exception.class, () -> a.service.proximity().syncWith(b.addr()));
            assertNull(b.find("Despues"));
        }
    }

    @Test
    void siElPuertoEstaOcupadoSeMuestraElMotivoReal() throws Exception {
        try (ServerSocket hold = new ServerSocket(0)) {
            char[] pwd = "clave-de-prueba-p".toCharArray();
            Path f = dir.resolve("p.ckey");
            Vault.create(f, pwd).close();
            PasswordManager m = new PasswordManager(f);
            m.unlock(pwd);
            SyncService s = new SyncService(m);
            s.setProximityPort(hold.getLocalPort());
            s.setProximityMulticast(false);
            String err = s.retryProximity();
            assertNotNull(err);
            assertTrue(err.contains("ocupado"), err);
            assertNull(s.proximity());
            hold.close();
            assertNull(s.retryProximity(), "al liberarse el puerto, el reintento arranca");
            s.stop();
            m.lock();
        }
    }
}
