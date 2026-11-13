package dev.persistence;

import dev.cipherkey.core.crypto.VaultCrypto;
import dev.cipherkey.core.crypto.VaultCrypto.KdfParams;
import dev.cipherkey.core.format.VaultContainer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Bóveda abierta: fichero .ckey + clave derivada en memoria. Almacén de bytes cifrados; el significado del contenido
 * (v2: array de entradas; v3: documento con registros sincronizables) lo decide quien la usa.
 * El formato binario vive en {@link VaultContainer} (módulo compartido con Android).
 *
 * <p>La KDF solo se ejecuta al abrir o al cambiar la clave maestra; guardar solo cifra.
 */
public final class Vault implements AutoCloseable {
    private final Path file;
    private byte[] header;
    private byte[] key;
    private final byte[] data;
    private final int version;

    private Vault(Path file, byte[] header, byte[] key, byte[] data, int version) {
        this.file = file;
        this.header = header;
        this.key = key;
        this.data = data;
        this.version = version;
    }

    /** Crea un fichero v3 nuevo con contenido vacío ({@code []}). Falla si ya existe. */
    public static Vault create(Path file, char[] password) throws VaultException {
        if (Files.exists(file)) {
            throw new VaultException("El archivo ya existe: " + file);
        }
        byte[] header = VaultContainer.newHeader(VaultContainer.VERSION_3, KdfParams.DEFAULT,
                VaultCrypto.randomBytes(VaultCrypto.SALT_LENGTH));
        byte[] key = VaultCrypto.deriveKey(password, VaultContainer.saltOf(header), KdfParams.DEFAULT);
        byte[] empty = "[]".getBytes(StandardCharsets.UTF_8);
        Vault vault = new Vault(file, header, key, empty, VaultContainer.VERSION_3);
        vault.write(empty, false);
        return vault;
    }

    public static Vault open(Path file, char[] password) throws VaultException {
        byte[] content;
        try {
            content = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new VaultException("No se pudo leer la bóveda: " + e.getMessage(), e);
        }
        try {
            VaultContainer.Opened o = VaultContainer.open(content, password);
            return new Vault(file, o.header(), o.key(), o.plaintext(), o.version());
        } catch (VaultContainer.FormatException e) {
            throw new VaultException.BadFormat(e.getMessage());
        } catch (VaultContainer.WrongPasswordException e) {
            throw new VaultException.InvalidPassword(e);
        } catch (GeneralSecurityException e) {
            throw new VaultException("Error al descifrar la bóveda", e);
        }
    }

    /** Versión del formato de fichero tal y como se leyó (2 = antiguo, 3 = actual). */
    public int version() {
        return version;
    }

    /** Contenido descifrado tal y como estaba al abrir la bóveda. */
    public byte[] data() {
        return data.clone();
    }

    /** Cifra y guarda de forma atómica, dejando la versión anterior como {@code <fichero>.bak}. */
    public void save(byte[] plaintext) throws VaultException {
        ensureOpen();
        write(plaintext, true);
    }

    /**
     * Migra un fichero v2 a v3 con el contenido ya convertido. Conserva el original como {@code <fichero>.v2.bak}
     * (nunca se sobrescribe una copia previa), escribe v3 con la misma sal y comprueba releyendo que descifra al mismo
     * contenido; si la comprobación falla, restaura el original.
     */
    public void upgradeToV3(byte[] plaintextV3) throws VaultException {
        ensureOpen();
        Path backup = file.resolveSibling(file.getFileName() + ".v2.bak");
        try {
            if (!Files.exists(backup)) Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException e) {
            throw new VaultException("No se pudo crear la copia de seguridad de la migración: " + e.getMessage(), e);
        }
        byte[] oldHeader = header;
        header = VaultContainer.withVersion(header, VaultContainer.VERSION_3);
        try {
            write(plaintextV3, false);
            byte[] back = Files.readAllBytes(file);
            byte[] check = VaultCrypto.decrypt(key, header, Arrays.copyOfRange(back, VaultContainer.HEADER_LENGTH, back.length));
            if (!Arrays.equals(check, plaintextV3) || VaultContainer.peekVersion(back) != VaultContainer.VERSION_3) {
                throw new VaultException("La verificación de la migración falló");
            }
        } catch (VaultException | IOException | GeneralSecurityException | VaultContainer.FormatException e) {
            header = oldHeader;
            try {
                Files.copy(backup, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // la copia .v2.bak sigue ahí para restaurar a mano
            }
            throw e instanceof VaultException ve ? ve : new VaultException("Migración revertida: " + e.getMessage(), e);
        }
    }

    /** Escribe una copia cifrada con la misma clave en otra ruta (atómica). No toca el fichero original. */
    public void exportCopy(Path target, byte[] plaintext) throws VaultException {
        ensureOpen();
        try {
            byte[] content = VaultContainer.seal(header, key, plaintext);
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(tmp, content);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | GeneralSecurityException e) {
            throw new VaultException("No se pudo exportar la copia: " + e.getMessage(), e);
        }
    }

    /** Cambia la clave maestra con una sal nueva y reescribe la bóveda. */
    public void changePassword(char[] newPassword, byte[] plaintext) throws VaultException {
        ensureOpen();
        byte[] newHeader = VaultContainer.newHeader(VaultContainer.VERSION_3, KdfParams.DEFAULT,
                VaultCrypto.randomBytes(VaultCrypto.SALT_LENGTH));
        byte[] newKey = VaultCrypto.deriveKey(newPassword, VaultContainer.saltOf(newHeader), KdfParams.DEFAULT);
        byte[] oldHeader = header;
        byte[] oldKey = key;
        header = newHeader;
        key = newKey;
        try {
            write(plaintext, true);
        } catch (VaultException e) {
            header = oldHeader;
            key = oldKey;
            Arrays.fill(newKey, (byte) 0);
            throw e;
        }
        Arrays.fill(oldKey, (byte) 0);
    }

    @Override
    public void close() {
        if (key != null) {
            Arrays.fill(key, (byte) 0);
            key = null;
        }
        Arrays.fill(data, (byte) 0);
    }

    private void ensureOpen() {
        if (key == null) {
            throw new IllegalStateException("La bóveda está cerrada");
        }
    }

    private void write(byte[] plaintext, boolean keepBackup) throws VaultException {
        byte[] content;
        try {
            content = VaultContainer.seal(header, key, plaintext);
        } catch (GeneralSecurityException e) {
            throw new VaultException("Error al cifrar la bóveda", e);
        }

        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.deleteIfExists(tmp);
            createPrivateFile(tmp);
            try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
                ch.write(ByteBuffer.wrap(content));
                ch.force(true);
            }
            if (keepBackup && Files.exists(file)) {
                Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // el error original es el relevante
            }
            throw new VaultException("No se pudo guardar la bóveda: " + e.getMessage(), e);
        }
    }

    private static void createPrivateFile(Path path) throws IOException {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.createFile(path, PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rw-------")));
        } else {
            Files.createFile(path);
        }
    }
}
