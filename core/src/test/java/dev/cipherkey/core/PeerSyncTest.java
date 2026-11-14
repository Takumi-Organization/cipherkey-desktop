package dev.cipherkey.core;

import dev.cipherkey.core.merge.Merger;
import dev.cipherkey.core.model.Record;
import dev.cipherkey.core.model.SyncState;
import dev.cipherkey.core.model.VaultDocument;
import dev.cipherkey.core.p2p.*;
import dev.cipherkey.core.sync.DeviceKey;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Sincronización directa entre dispositivos: convergencia, conflictos, confianza y ataques básicos. */
class PeerSyncTest {
    /** Un dispositivo: documento en memoria con cerrojo, servicio de proximidad en loopback y respuestas del usuario simuladas. */
    static final class Node implements Locked, AutoCloseable {
        final VaultDocument doc;
        final PeerService service;
        final List<String> sasSeen = Collections.synchronizedList(new ArrayList<>());
        final List<String> errors = Collections.synchronizedList(new ArrayList<>());
        volatile boolean acceptSas = true;
        final AtomicInteger syncs = new AtomicInteger();

        Node(VaultDocument doc, String id, String name) throws Exception {
            this.doc = doc;
            SyncState s = new SyncState();
            s.deviceId = id; s.deviceName = name; s.devicePrivateKey = DeviceKey.generate().privateBase64();
            s.serverUrl = "http://unused";
            doc.sync = s;
            service = new PeerService(this, 0, false,
                    (peer, sas, incoming) -> { sasSeen.add(sas); return acceptSas; },
                    new PeerService.Listener() {
                        @Override public void synced(PeerSync.Result r, boolean incoming) { syncs.incrementAndGet(); }
                        @Override public void failed(String who, String message, boolean incoming) { errors.add(message); }
                    }, System::currentTimeMillis);
            service.start();
        }

        @Override public synchronized <T> T run(Work<T> w) throws Exception { return w.apply(doc); }
        InetSocketAddress addr() { return new InetSocketAddress("127.0.0.1", service.port()); }
        @Override public void close() { service.close(); }
        Record find(String uuid) { return doc.find(uuid).orElse(null); }
    }

    static Record rec(String title, String pass) {
        Record r = Record.create("seed", 1000);
        r.title = title; r.password = pass; r.username = "u";
        return r;
    }

    /** Dos nodos de la misma bóveda (mismo vaultId y clave de sincronización). */
    static Node[] pair() throws Exception {
        VaultDocument a = new VaultDocument();
        VaultDocument b = new VaultDocument();
        b.vaultId = a.vaultId; b.syncKey = a.syncKey;
        return new Node[]{new Node(a, "dev-A", "Portátil"), new Node(b, "dev-B", "Móvil")};
    }

    static void link(Node a, Node b) throws Exception {
        b.service.openPairingWindow(60_000);
        a.service.syncWith(b.addr(), true);
    }

    @Test void primerVinculoConfirmaElMismoCodigoYSincroniza() throws Exception {
        Node[] n = pair();
        try (Node a = n[0]; Node b = n[1]) {
            Record r = rec("Banco", "p1");
            a.doc.records.add(r);
            link(a, b);
            assertEquals(1, a.sasSeen.size());
            assertEquals(a.sasSeen.get(0), b.sasSeen.get(0), "el código de seguridad debe coincidir");
            assertTrue(a.sasSeen.get(0).matches("\\d{6}"));
            assertEquals("p1", b.find(r.uuid).password);
            assertTrue(a.doc.sync.peers.containsKey("dev-B") && b.doc.sync.peers.containsKey("dev-A"));
            assertTrue(b.doc.sync.pending.containsKey(r.uuid), "lo recibido debe quedar marcado para subir al servidor");
        }
    }

    @Test void siguienteSincronizacionNoPideCodigoYAplicaCambiosEnAmbosSentidos() throws Exception {
        Node[] n = pair();
        try (Node a = n[0]; Node b = n[1]) {
            Record r1 = rec("Uno", "x"), r2 = rec("Dos", "y");
            a.doc.records.add(r1);
            link(a, b);
            int sas = a.sasSeen.size();
            a.find(r1.uuid).password = "x2"; a.find(r1.uuid).touch("dev-A", 2000);
            b.doc.records.add(r2);
            a.service.syncWith(b.addr(), false);
            assertEquals(sas, a.sasSeen.size(), "ya vinculados: sin código");
            assertEquals("x2", b.find(r1.uuid).password);
            assertEquals("y", a.find(r2.uuid).password);
        }
    }

    @Test void camposDistintosSeFusionanYElMismoCampoDejaConflicto() throws Exception {
        Node[] n = pair();
        try (Node a = n[0]; Node b = n[1]) {
            Record r = rec("Cuenta", "base");
            a.doc.records.add(r);
            link(a, b);
            // A cambia el usuario, B cambia las notas: se fusionan
            a.find(r.uuid).username = "ana"; a.find(r.uuid).touch("dev-A", 2000);
            b.find(r.uuid).notes = "nota de B"; b.find(r.uuid).touch("dev-B", 2100);
            a.service.syncWith(b.addr(), false);
            for (Node x : List.of(a, b)) {
                assertEquals("ana", x.find(r.uuid).username);
                assertEquals("nota de B", x.find(r.uuid).notes);
                assertNull(x.find(r.uuid).conflict);
            }
            // mismo campo distinto: conflicto, y no se pierde ninguna de las dos versiones
            a.find(r.uuid).password = "de-A"; a.find(r.uuid).touch("dev-A", 3000);
            b.find(r.uuid).password = "de-B"; b.find(r.uuid).touch("dev-B", 3100);
            a.service.syncWith(b.addr(), false);
            Set<String> seen = new HashSet<>();
            for (Node x : List.of(a, b)) {
                Record c = x.find(r.uuid);
                seen.add(c.password);
                assertNotNull(c.conflict, "cada lado conserva la otra versión");
                seen.add(c.conflict.password);
            }
            assertEquals(Set.of("de-A", "de-B"), seen);
        }
    }

    @Test void borradoSePropagaComoLapida() throws Exception {
        Node[] n = pair();
        try (Node a = n[0]; Node b = n[1]) {
            Record r = rec("Temporal", "t");
            a.doc.records.add(r);
            link(a, b);
            a.find(r.uuid).markDeleted("dev-A", 5000);
            a.service.syncWith(b.addr(), false);
            assertTrue(b.find(r.uuid).deleted);
        }
    }

    @Test void codigoRechazadoNoVinculaNiIntercambiaDatos() throws Exception {
        Node[] n = pair();
        try (Node a = n[0]; Node b = n[1]) {
            Record r = rec("Secreto", "s");
            a.doc.records.add(r);
            b.acceptSas = false; // el usuario del segundo dispositivo ve que no coincide
            b.service.openPairingWindow(60_000);
            assertThrows(IOException.class, () -> a.service.syncWith(b.addr(), true));
            assertTrue(a.doc.sync.peers.isEmpty() && b.doc.sync.peers.isEmpty());
            assertNull(b.find(r.uuid));
        }
    }

    @Test void sinModoVincularUnDispositivoNuevoNoSeAcepta() throws Exception {
        Node[] n = pair();
        try (Node a = n[0]; Node b = n[1]) {
            a.doc.records.add(rec("X", "x"));
            // b no abrió la ventana de vincular
            assertThrows(IOException.class, () -> a.service.syncWith(b.addr(), true));
            assertTrue(b.doc.records.isEmpty());
            assertTrue(a.doc.sync.peers.isEmpty());
        }
    }

    @Test void dispositivoAjenoSinLaClaveNoCompletaElApreton() throws Exception {
        Node[] n = pair();
        try (Node a = n[0]; Node b = n[1]) {
            b.service.openPairingWindow(60_000);
            VaultDocument other = new VaultDocument(); // otra bóveda: otra clave de sincronización
            Node intruder = new Node(other, "evil", "Intruso");
            try (intruder) {
                assertThrows(PeerAuthException.class, () -> intruder.service.syncWith(b.addr(), true));
            }
            assertTrue(b.doc.sync.peers.isEmpty());
        }
    }

    @Test void intermediarioActivoSinLaClaveNoPuedeSuplantarAlRespondedor() throws Exception {
        Node[] n = pair();
        try (Node a = n[0]; Node b = n[1]) {
            // el atacante escucha donde A cree que está B y contesta con su propia clave de sincronización
            VaultDocument evilDoc = new VaultDocument();
            Node evil = new Node(evilDoc, "dev-B", "Falso B");
            try (evil) {
                evil.service.openPairingWindow(60_000);
                a.doc.records.add(rec("Secreto", "no-debe-salir"));
                assertThrows(PeerAuthException.class, () -> a.service.syncWith(evil.addr(), true));
                assertTrue(evilDoc.records.isEmpty(), "el intermediario no recibe ningún registro");
                assertTrue(a.sasSeen.isEmpty(), "ni siquiera se llega a mostrar un código");
            }
        }
    }

    /** Proxy TCP que permite observar, alterar o grabar los bytes entre dos nodos. */
    static final class Proxy implements AutoCloseable {
        final ServerSocket ss;
        final ByteArrayOutputStream clientToServer = new ByteArrayOutputStream();
        volatile int flipAt = -1; // índice de byte (cliente->servidor) a alterar
        Proxy(InetSocketAddress target) throws IOException {
            ss = new ServerSocket(0);
            Thread t = new Thread(() -> {
                try {
                    Socket c = ss.accept();
                    Socket s = new Socket(target.getAddress(), target.getPort());
                    pump(c.getInputStream(), s.getOutputStream(), true);
                    pump(s.getInputStream(), c.getOutputStream(), false);
                } catch (IOException ignored) { }
            });
            t.setDaemon(true); t.start();
        }
        private void pump(InputStream in, OutputStream out, boolean record) {
            Thread t = new Thread(() -> {
                try {
                    int pos = 0, b;
                    while ((b = in.read()) >= 0) {
                        if (record) { synchronized (clientToServer) { clientToServer.write(b); } if (pos == flipAt) b ^= 1; pos++; }
                        out.write(b); out.flush();
                    }
                } catch (IOException ignored) { }
            });
            t.setDaemon(true); t.start();
        }
        InetSocketAddress addr() { return new InetSocketAddress("127.0.0.1", ss.getLocalPort()); }
        byte[] recorded() { synchronized (clientToServer) { return clientToServer.toByteArray(); } }
        @Override public void close() throws IOException { ss.close(); }
    }

    @Test void unBitAlteradoEnLaRedHaceFallarLaSesionSinAplicarNada() throws Exception {
        Node[] n = pair();
        try (Node a = n[0]; Node b = n[1]) {
            Record r = rec("Dato", "d");
            a.doc.records.add(r);
            link(a, b); // vinculados
            a.find(r.uuid).password = "nuevo"; a.find(r.uuid).touch("dev-A", 9000);
            try (Proxy p = new Proxy(b.addr())) {
                p.flipAt = 300; // dentro de las tramas cifradas posteriores al apretón de manos
                assertThrows(IOException.class, () -> a.service.syncWith(p.addr(), false));
            }
            assertEquals("d", b.find(r.uuid).password, "una sesión manipulada no cambia nada");
        }
    }

    @Test void repetirUnaSesionGrabadaNoSirve() throws Exception {
        Node[] n = pair();
        try (Node a = n[0]; Node b = n[1]) {
            Record r = rec("Dato", "d");
            a.doc.records.add(r);
            link(a, b);
            a.find(r.uuid).password = "cambio"; a.find(r.uuid).touch("dev-A", 9000);
            byte[] recorded;
            try (Proxy p = new Proxy(b.addr())) {
                a.service.syncWith(p.addr(), false);
                Thread.sleep(200);
                recorded = p.recorded();
            }
            assertEquals("cambio", b.find(r.uuid).password);
            // el atacante repite lo que A envió, contra B
            b.find(r.uuid).password = "otro"; b.find(r.uuid).touch("dev-B", 9500);
            b.errors.clear();
            try (Socket s = new Socket("127.0.0.1", b.service.port())) {
                s.setSoTimeout(3000);
                s.getOutputStream().write(recorded);
                s.getOutputStream().flush();
                try { s.getInputStream().readAllBytes(); } catch (IOException ignored) { }
            }
            Thread.sleep(300);
            assertEquals("otro", b.find(r.uuid).password, "la repetición no debe aplicar nada");
            assertFalse(b.errors.isEmpty(), "B debe registrar el intento como fallo de autenticación");
        }
    }

    @Test void variosIntentosFallidosBloqueanElOrigen() throws Exception {
        Node[] n = pair();
        try (Node a = n[0]; Node b = n[1]) {
            for (int i = 0; i < 6; i++) {
                try (Socket s = new Socket("127.0.0.1", b.service.port())) {
                    s.getOutputStream().write(new byte[80]); // basura
                    s.getOutputStream().flush();
                    s.setSoTimeout(2000);
                    try { s.getInputStream().readAllBytes(); } catch (IOException ignored) { }
                }
            }
            Thread.sleep(200);
            int before = b.errors.size();
            try (Socket s = new Socket("127.0.0.1", b.service.port())) {
                s.getOutputStream().write(new byte[80]);
                s.setSoTimeout(2000);
                try { s.getInputStream().readAllBytes(); } catch (IOException ignored) { }
            }
            Thread.sleep(200);
            assertEquals(before, b.errors.size(), "pasado el límite, el origen se rechaza sin ni siquiera procesarlo");
            // y un par legítimo desde la misma IP también queda bloqueado un tiempo
            assertThrows(IOException.class, () -> a.service.syncWith(b.addr(), true));
        }
    }

    @Test void claveFijadaQueCambiaAbortaPorPosibleSuplantacion() throws Exception {
        Node[] n = pair();
        try (Node a = n[0]; Node b = n[1]) {
            link(a, b);
            // A recuerda otra clave pública para B (como si alguien la hubiera cambiado)
            a.doc.sync.peers.get("dev-B").publicKey = DeviceKey.generate().publicBase64();
            PeerAuthException e = assertThrows(PeerAuthException.class, () -> a.service.syncWith(b.addr(), false));
            assertTrue(e.getMessage().contains("cambiado"));
        }
    }

    @Test void anuncioDeDescubrimientoSoloLoVerificanLosMiembros() {
        byte[] key = new VaultDocument().syncKey.getBytes();
        byte[] other = new byte[32];
        byte[] k1 = Base64.getDecoder().decode(new VaultDocument().syncKey);
        byte[] beacon = Beacon.build(k1, "dev-A", 47890);
        assertEquals(Beacon.LENGTH, beacon.length);
        Beacon.Parsed ok = Beacon.parse(k1, beacon, beacon.length);
        assertNotNull(ok);
        assertEquals(47890, ok.port());
        assertArrayEquals(Beacon.idTag(k1, "dev-A"), ok.idTag());
        assertNull(Beacon.parse(other, beacon, beacon.length), "otra bóveda no lo valida");
        byte[] forged = beacon.clone(); forged[5] ^= 1; // cambia el puerto
        assertNull(Beacon.parse(k1, forged, forged.length), "un anuncio alterado se descarta");
        assertNull(Beacon.parse(k1, new byte[Beacon.LENGTH], Beacon.LENGTH));
    }

    @Test void limitadorDeIntentosCaducaConElTiempo() {
        long[] now = {0};
        AttemptLimiter l = new AttemptLimiter(3, 1000, () -> now[0]);
        for (int i = 0; i < 3; i++) l.fail("x");
        assertTrue(l.blocked("x"));
        assertFalse(l.blocked("y"));
        now[0] = 2000;
        assertFalse(l.blocked("x"));
        l.fail("x"); l.success("x");
        assertFalse(l.blocked("x"));
    }
}
