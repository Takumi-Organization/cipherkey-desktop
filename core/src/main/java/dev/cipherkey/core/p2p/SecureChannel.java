package dev.cipherkey.core.p2p;

import dev.cipherkey.core.crypto.VaultCrypto;
import dev.cipherkey.core.sync.DeviceKey;
import org.bouncycastle.crypto.agreement.X25519Agreement;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Canal cifrado y autenticado entre dos dispositivos de la misma bóveda, sobre cualquier par de flujos (TCP en LAN,
 * RFCOMM en Bluetooth...). Ver docs/adr/0001-sincronizacion-en-proximidad.md.
 *
 * <p>Apretón de manos: X25519 efímero en ambos lados (secreto hacia delante) y transcripto T con todo lo enviado.
 * Antes de revelar nada, cada lado exige una etiqueta HMAC con la clave de sincronización de la bóveda (solo la tienen
 * los miembros): un tercero que no la tenga no puede completar el apretón ni hacer de intermediario. Después cada
 * dispositivo prueba su identidad con una firma Ed25519 sobre T y un MAC de pertenencia. El código de seguridad (SAS) de
 * 6 dígitos sale de T y de las dos claves públicas: ambos lados deben mostrar el mismo.
 *
 * <p>Tramas: longitud(4) + AES-256-GCM(nonce aleatorio, AAD = dirección + contador). El contador estricto impide
 * repetir, reordenar o eliminar tramas.
 */
public final class SecureChannel implements Closeable {
    public static final int MAX_FRAME = 16 * 1024 * 1024;
    private static final byte[] MAGIC = {'C', 'K', 'P', '2', 'P', 1};
    private static final SecureRandom RNG = new SecureRandom();

    /** Quién es el otro extremo, ya probado criptográficamente (pertenencia + clave), pero aún sin confianza fijada. */
    public record Peer(String deviceId, String name, String publicKeyB64, String sas) {}

    private final DataInputStream in;
    private final DataOutputStream out;
    private final Closeable closer;
    private final boolean initiator;
    private byte[] kSend, kRecv;
    private long sendCtr, recvCtr;
    private Peer peer;

    private SecureChannel(InputStream in, OutputStream out, Closeable closer, boolean initiator) {
        this.in = new DataInputStream(new BufferedInputStream(in));
        this.out = new DataOutputStream(new BufferedOutputStream(out));
        this.closer = closer;
        this.initiator = initiator;
    }

    public Peer peer() { return peer; }

    /** Abre el canal como iniciador. Lanza {@link PeerAuthException} si el otro extremo no es miembro de la bóveda. */
    public static SecureChannel connect(InputStream in, OutputStream out, Closeable closer, byte[] syncKey,
                                        DeviceKey identity, String deviceId, String deviceName) throws IOException {
        SecureChannel c = new SecureChannel(in, out, closer, true);
        try {
            c.handshake(syncKey, identity, deviceId, deviceName);
        } catch (EOFException e) {
            c.closeQuietly();
            throw new PeerAuthException("El dispositivo cerró la conexión: no pertenece a esta bóveda o no acepta conexiones");
        } catch (IOException | RuntimeException e) {
            c.closeQuietly();
            throw e;
        } catch (GeneralSecurityException e) {
            c.closeQuietly();
            throw new PeerAuthException("Fallo criptográfico en el apretón de manos");
        }
        return c;
    }

    public static SecureChannel accept(InputStream in, OutputStream out, Closeable closer, byte[] syncKey,
                                       DeviceKey identity, String deviceId, String deviceName) throws IOException {
        SecureChannel c = new SecureChannel(in, out, closer, false);
        try {
            c.handshake(syncKey, identity, deviceId, deviceName);
        } catch (IOException | RuntimeException e) {
            c.closeQuietly();
            throw e;
        } catch (GeneralSecurityException e) {
            c.closeQuietly();
            throw new PeerAuthException("Fallo criptográfico en el apretón de manos");
        }
        return c;
    }

    private void handshake(byte[] syncKey, DeviceKey identity, String myId, String myName) throws IOException, GeneralSecurityException {
        byte[] kdisc = VaultCrypto.hkdf(syncKey, "ck-p2p-vtag");
        byte[] member = VaultCrypto.hkdf(syncKey, "ck-p2p-member");
        X25519PrivateKeyParameters eph = new X25519PrivateKeyParameters(RNG);
        byte[] myE = eph.generatePublicKey().getEncoded();
        byte[] myN = VaultCrypto.randomBytes(16);
        byte[] peerE, peerN;
        byte[] eI, nI, eR, nR;

        if (initiator) {
            out.write(MAGIC); out.write(myE); out.write(myN);
            out.write(Arrays.copyOf(hmac(kdisc, "m1", myE, myN), 16));
            out.flush();
            byte[] magic = readFully(MAGIC.length);
            peerE = readFully(32); peerN = readFully(16);
            byte[] tag = readFully(16);
            if (!MessageDigest.isEqual(magic, MAGIC)) throw new PeerAuthException("No es un par de CipherKey");
            if (!MessageDigest.isEqual(tag, Arrays.copyOf(hmac(kdisc, "m2", peerE, peerN, myE, myN), 16)))
                throw new PeerAuthException("El dispositivo no pertenece a esta bóveda");
            eI = myE; nI = myN; eR = peerE; nR = peerN;
        } else {
            byte[] magic = readFully(MAGIC.length);
            peerE = readFully(32); peerN = readFully(16);
            byte[] tag = readFully(16);
            if (!MessageDigest.isEqual(magic, MAGIC)) throw new PeerAuthException("No es un par de CipherKey");
            if (!MessageDigest.isEqual(tag, Arrays.copyOf(hmac(kdisc, "m1", peerE, peerN), 16)))
                throw new PeerAuthException("El dispositivo no pertenece a esta bóveda");
            out.write(MAGIC); out.write(myE); out.write(myN);
            out.write(Arrays.copyOf(hmac(kdisc, "m2", myE, myN, peerE, peerN), 16));
            out.flush();
            eI = peerE; nI = peerN; eR = myE; nR = myN;
        }

        byte[] shared = new byte[32];
        X25519Agreement agreement = new X25519Agreement();
        agreement.init(eph);
        agreement.calculateAgreement(new X25519PublicKeyParameters(peerE, 0), shared, 0);
        byte[] t = VaultCrypto.sha256(concat("CKP2P-T1".getBytes(StandardCharsets.UTF_8), eI, nI, eR, nR));
        byte[] base = concat(shared, t);
        byte[] k1 = VaultCrypto.hkdf(base, "ck-p2p-i2r");
        byte[] k2 = VaultCrypto.hkdf(base, "ck-p2p-r2i");
        kSend = initiator ? k1 : k2;
        kRecv = initiator ? k2 : k1;
        Arrays.fill(shared, (byte) 0);

        // Autenticación: el iniciador habla primero; el respondedor solo contesta si el MAC de pertenencia es válido.
        byte myRole = (byte) (initiator ? 'I' : 'R');
        String myPub = identity.publicBase64();
        byte[] mySig = identity.sign(concat(t, new byte[]{myRole}, myId.getBytes(StandardCharsets.UTF_8), myPub.getBytes(StandardCharsets.UTF_8)));
        byte[] myMac = hmac(member, "auth", t, new byte[]{myRole}, myId.getBytes(StandardCharsets.UTF_8), myPub.getBytes(StandardCharsets.UTF_8));
        Frame mine = new Frame().put("id", myId).put("name", myName).put("pub", myPub)
                .put("sig", Base64.getEncoder().encodeToString(mySig)).put("mac", Base64.getEncoder().encodeToString(myMac));

        Frame theirs;
        if (initiator) {
            sendFrame(mine.encode());
            theirs = Frame.decode(recvFrame());
        } else {
            theirs = Frame.decode(recvFrame());
            verifyAuth(theirs, member, t, (byte) 'I');
            sendFrame(mine.encode());
        }
        if (initiator) verifyAuth(theirs, member, t, (byte) 'R');

        String theirPub = theirs.get("pub");
        byte[] pubI = Base64.getDecoder().decode(initiator ? myPub : theirPub);
        byte[] pubR = Base64.getDecoder().decode(initiator ? theirPub : myPub);
        byte[] sasHash = VaultCrypto.sha256(concat("CKP2P-SAS".getBytes(StandardCharsets.UTF_8), t, pubI, pubR));
        int v = ((sasHash[0] & 0x7f) << 24 | (sasHash[1] & 0xff) << 16 | (sasHash[2] & 0xff) << 8 | (sasHash[3] & 0xff)) % 1_000_000;
        peer = new Peer(theirs.get("id"), theirs.get("name"), theirPub, String.format("%06d", v));
    }

    private void verifyAuth(Frame f, byte[] member, byte[] t, byte role) throws PeerAuthException {
        try {
            String id = f.get("id"), pub = f.get("pub");
            byte[] expected = hmac(member, "auth", t, new byte[]{role}, id.getBytes(StandardCharsets.UTF_8), pub.getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(expected, Base64.getDecoder().decode(f.get("mac"))))
                throw new PeerAuthException("Prueba de pertenencia a la bóveda no válida");
            byte[] msg = concat(t, new byte[]{role}, id.getBytes(StandardCharsets.UTF_8), pub.getBytes(StandardCharsets.UTF_8));
            if (!DeviceKey.verify(pub, msg, Base64.getDecoder().decode(f.get("sig"))))
                throw new PeerAuthException("Firma del dispositivo no válida");
        } catch (GeneralSecurityException | RuntimeException e) {
            if (e instanceof PeerAuthException p) throw p;
            throw new PeerAuthException("Autenticación del par mal formada");
        }
    }

    // ------------------------------------------------------------------ tramas

    public synchronized void send(Frame f) throws IOException {
        try {
            sendFrame(f.encode());
        } catch (GeneralSecurityException e) {
            throw new IOException("No se pudo cifrar la trama");
        }
    }

    public Frame receive() throws IOException {
        try {
            return Frame.decode(recvFrame());
        } catch (GeneralSecurityException e) {
            throw new PeerAuthException("Trama no válida (manipulada o repetida)");
        }
    }

    private void sendFrame(byte[] plain) throws IOException, GeneralSecurityException {
        byte[] ct = VaultCrypto.encrypt(kSend, aad(initiator, sendCtr), plain);
        sendCtr++;
        out.writeInt(ct.length);
        out.write(ct);
        out.flush();
    }

    private byte[] recvFrame() throws IOException, GeneralSecurityException {
        int len = in.readInt();
        if (len < 0 || len > MAX_FRAME) throw new PeerAuthException("Trama demasiado grande");
        byte[] ct = readFully(len);
        byte[] plain = VaultCrypto.decrypt(kRecv, aad(!initiator, recvCtr), ct);
        recvCtr++;
        return plain;
    }

    private static byte[] aad(boolean fromInitiator, long ctr) {
        return java.nio.ByteBuffer.allocate(9).put((byte) (fromInitiator ? 'I' : 'R')).putLong(ctr).array();
    }

    // ------------------------------------------------------------------ utilidades

    private byte[] readFully(int n) throws IOException {
        byte[] b = new byte[n];
        in.readFully(b);
        return b;
    }

    static byte[] hmac(byte[] key, String label, byte[]... parts) throws GeneralSecurityException {
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(new SecretKeySpec(key, "HmacSHA256"));
        m.update(label.getBytes(StandardCharsets.UTF_8));
        for (byte[] p : parts) { m.update((byte) (p.length >> 8)); m.update((byte) p.length); m.update(p); }
        return m.doFinal();
    }

    private static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) n += p.length;
        byte[] o = new byte[n];
        int i = 0;
        for (byte[] p : parts) { System.arraycopy(p, 0, o, i, p.length); i += p.length; }
        return o;
    }

    private void closeQuietly() {
        try { close(); } catch (IOException ignored) { }
    }

    @Override public void close() throws IOException {
        if (kSend != null) Arrays.fill(kSend, (byte) 0);
        if (kRecv != null) Arrays.fill(kRecv, (byte) 0);
        if (closer != null) closer.close();
    }
}
