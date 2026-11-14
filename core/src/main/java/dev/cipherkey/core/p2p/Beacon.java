package dev.cipherkey.core.p2p;

import dev.cipherkey.core.crypto.VaultCrypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Anuncio de presencia en la red local. Solo los miembros de la bóveda (que tienen la clave de sincronización) pueden
 * comprobar su etiqueta; para cualquier otro es ruido. No contiene el nombre ni datos de la bóveda, solo el puerto y una
 * etiqueta corta del dispositivo. Un anuncio falso solo puede hacer que se intente una conexión, que fallará en el
 * apretón de manos.
 */
public final class Beacon {
    private Beacon() {}

    public static final int LENGTH = 4 + 2 + 8 + 8 + 16;
    private static final byte[] MAGIC = "CKB1".getBytes(StandardCharsets.US_ASCII);

    public record Parsed(int port, byte[] idTag) {}

    /** Etiqueta corta estable de un dispositivo, comparable por los miembros de la bóveda. */
    public static byte[] idTag(byte[] syncKey, String deviceId) {
        try {
            return Arrays.copyOf(SecureChannel.hmac(VaultCrypto.hkdf(syncKey, "ck-p2p-vtag"), "id", deviceId.getBytes(StandardCharsets.UTF_8)), 8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] build(byte[] syncKey, String deviceId, int port) {
        byte[] nonce = VaultCrypto.randomBytes(8);
        byte[] tag = idTag(syncKey, deviceId);
        ByteBuffer b = ByteBuffer.allocate(LENGTH);
        b.put(MAGIC).putShort((short) port).put(nonce).put(tag).put(mac(syncKey, port, nonce, tag));
        return b.array();
    }

    /** Devuelve null si no es un anuncio válido de esta bóveda. */
    public static Parsed parse(byte[] syncKey, byte[] data, int len) {
        if (len != LENGTH) return null;
        ByteBuffer b = ByteBuffer.wrap(data, 0, len);
        byte[] magic = new byte[4]; b.get(magic);
        if (!Arrays.equals(magic, MAGIC)) return null;
        int port = b.getShort() & 0xffff;
        byte[] nonce = new byte[8]; b.get(nonce);
        byte[] tag = new byte[8]; b.get(tag);
        byte[] mac = new byte[16]; b.get(mac);
        if (!MessageDigest.isEqual(mac, mac(syncKey, port, nonce, tag))) return null;
        return new Parsed(port, tag);
    }

    private static byte[] mac(byte[] syncKey, int port, byte[] nonce, byte[] tag) {
        try {
            byte[] p = {(byte) (port >> 8), (byte) port};
            return Arrays.copyOf(SecureChannel.hmac(VaultCrypto.hkdf(syncKey, "ck-p2p-vtag"), "bcn", p, nonce, tag), 16);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
