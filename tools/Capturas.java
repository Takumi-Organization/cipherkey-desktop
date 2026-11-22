package dev.view;

import dev.controller.PasswordManager;
import dev.model.PasswordEntry;
import dev.persistence.AppPrefs;
import dev.persistence.Vault;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Genera las capturas del README a partir de la aplicación REAL en marcha (no son maquetas), con una bóveda de
 * demostración de datos inventados. Pinta cada ventana en un PNG con Component.paint, sin tocar el escritorio del usuario.
 *
 * <p>Uso (necesita un servidor X; p. ej. Xwayland sobre un weston headless, o xvfb-run):
 * <pre>
 *   mvn -q compile
 *   javac -cp target/classes:$(cat cp.txt) -d /ruta/salida tools/Capturas.java
 *   DISPLAY=:77 java -Duser.home=/ruta/temporal -cp /ruta/salida:target/classes:$(cat cp.txt) dev.view.Capturas docs/capturas
 * </pre>
 * Con {@code -Duser.home} apuntando a un directorio temporal las preferencias no tocan las del usuario.
 */
public final class Capturas {
    private static Path out;

    public static void main(String[] args) throws Exception {
        out = Path.of(args.length > 0 ? args[0] : "docs/capturas");
        Files.createDirectories(out);
        // La bóveda de demostración vive en un directorio neutro: la ruta se ve en la pantalla de acceso.
        Path dir = Path.of("/dev/shm/ckdemo");
        Files.createDirectories(dir);
        Path vault = dir.resolve("demo.ckey");
        Files.deleteIfExists(vault);
        char[] pwd = "demo-boveda-2026".toCharArray();
        Vault.create(vault, pwd).close();
        PasswordManager manager = new PasswordManager(vault);
        manager.unlock(pwd);
        for (PasswordEntry e : demo()) manager.addEntry(e);
        AppPrefs.addRecentVault(vault);
        AppPrefs.setWindowSize(1280, 780);
        AppPrefs.setReduceMotion(true);

        for (boolean dark : new boolean[]{true, false}) {
            AppPrefs.setDark(dark);
            ThemeManager.install();
            String suffix = dark ? "oscuro" : "papel";
            login(vault, "acceso-" + suffix);
            MainFrame[] fr = new MainFrame[1];
            onEdt(() -> {
                fr[0] = new MainFrame(manager);
                fr[0].setSize(1280, 780);
                fr[0].setVisible(true);
            });
            pause(900);
            main(fr[0], manager, suffix);
            onEdt(() -> {
                fr[0].setVisible(false);
                fr[0].dispose();
            });
        }
        System.exit(0);
    }

    private static void main(MainFrame f, PasswordManager manager, String suffix) throws Exception {
        // lista con una entrada con 2FA seleccionada
        onEdt(() -> {
            EntryTable t = find(f, EntryTable.class);
            for (int i = 0; i < t.getRowCount(); i++) {
                if ("Correo Norte".equals(t.getValueAt(i, 0) instanceof PasswordEntry e ? e.getTitle() : null)) t.setRowSelectionInterval(i, i);
            }
        });
        pause(500);
        shot(f, "principal-" + suffix);
        if (!suffix.equals("oscuro")) {
            // salud en papel
            onEdt(() -> ((EntryTable) find(f, EntryTable.class)).clearSelection());
            selectView(f, "Salud de la bóveda");
            pause(900);
            shot(f, "salud-" + suffix);
            selectView(f, "Todas las entradas");
            return;
        }
        // salud de la bóveda
        onEdt(() -> ((EntryTable) find(f, EntryTable.class)).clearSelection());
        selectView(f, "Salud de la bóveda");
        pause(900);
        shot(f, "salud-" + suffix);
        selectView(f, "Todas las entradas");
        pause(300);
        // detalle de una entrada con avisos (débil, antigua)
        onEdt(() -> {
            EntryTable t = find(f, EntryTable.class);
            for (int i = 0; i < t.getRowCount(); i++) {
                if (t.getValueAt(i, 0) instanceof PasswordEntry e && "Biblioteca Municipal".equals(e.getTitle())) t.setRowSelectionInterval(i, i);
            }
        });
        pause(500);
        shot(f, "detalle-" + suffix);
        // generador
        JDialog[] d = new JDialog[1];
        onEdt(() -> {
            d[0] = new PasswordGeneratorDialog(f, false);
            d[0].setModalityType(Dialog.ModalityType.MODELESS);
            d[0].setVisible(true);
        });
        pause(500);
        shot(d[0], "generador-" + suffix);
        onEdt(d[0]::dispose);
        // entrada (edición)
        onEdt(() -> {
            EntryTable t = find(f, EntryTable.class);
            for (int i = 0; i < t.getRowCount(); i++) {
                if (t.getValueAt(i, 0) instanceof PasswordEntry e && "Banco Ejemplo".equals(e.getTitle())) t.setRowSelectionInterval(i, i);
            }
            PasswordEntry sel = t.selectedEntry();
            d[0] = new EntryDialog(f, sel, List.of("Trabajo", "Finanzas", "Compras", "Social", "Ocio", "Hogar"));
            d[0].setModalityType(Dialog.ModalityType.MODELESS);
            d[0].setVisible(true);
        });
        pause(500);
        shot(d[0], "entrada-" + suffix);
        onEdt(d[0]::dispose);
        // ajustes
        onEdt(() -> {
            d[0] = new SettingsDialog(f, manager);
            d[0].setModalityType(Dialog.ModalityType.MODELESS);
            d[0].setVisible(true);
        });
        pause(500);
        shot(d[0], "ajustes-" + suffix);
        onEdt(d[0]::dispose);
        // sincronización
        onEdt(() -> {
            dev.sync.SyncService svc;
            try {
                // el servicio de la propia ventana (ya arrancado, igual que en el uso real)
                java.lang.reflect.Field fld = MainFrame.class.getDeclaredField("syncService");
                fld.setAccessible(true);
                svc = (dev.sync.SyncService) fld.get(f);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            d[0] = new SyncDialog(f, svc, () -> { });
            d[0].setModalityType(Dialog.ModalityType.MODELESS);
            d[0].setVisible(true);
        });
        onEdt(() -> {
            d[0].setSize(800, 880);
            // el nombre del dispositivo sale del nombre del equipo: se sustituye por uno inventado
            String host;
            try { host = java.net.InetAddress.getLocalHost().getHostName(); } catch (Exception e) { host = ""; }
            renameHost(d[0], host);
        });
        pause(3000);
        shot(d[0], "sincronizacion-" + suffix);
        onEdt(d[0]::dispose);
    }

    // ---- escenas ----

    private static void login(Path vault, String name) throws Exception {
        LoginDialog[] d = new LoginDialog[1];
        onEdt(() -> {
            d[0] = new LoginDialog(null, vault, null);
            d[0].setModalityType(Dialog.ModalityType.MODELESS);
            d[0].setVisible(true);
        });
        pause(700);
        shot(d[0], name);
        onEdt(d[0]::dispose);
    }

    private static void selectView(MainFrame f, String label) throws Exception {
        onEdt(() -> {
            Sidebar s = find(f, Sidebar.class);
            JList<?> l = find(s, JList.class);
            for (int i = 0; i < l.getModel().getSize(); i++) {
                Object v = l.getModel().getElementAt(i);
                if (v instanceof Sidebar.View view && view.label().equals(label)) l.setSelectedIndex(i);
            }
        });
    }

    private static List<PasswordEntry> demo() {
        List<PasswordEntry> l = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        l.add(entry("Correo Norte", "marta.ruiz", "Cielo-Abierto-77-Faro!", "https://mail.demo.test", "Trabajo", "Buzón principal de la oficina.", true, "JBSWY3DPEHPK3PXP", now.minusDays(40)));
        l.add(entry("Banco Ejemplo", "mruiz", "T9#kd2$Lq8vP!x4Zr", "https://banca.demo.test", "Finanzas", "", true, "GEZDGNBVGY3TQOJQ", now.minusDays(12)));
        l.add(entry("Tienda Aurora", "marta.ruiz@demo.test", "Verano2020", "https://tienda.demo.test", "Compras", "", false, null, now.minusDays(500)));
        l.add(entry("Foro de Senderismo", "martaR", "Verano2020", "https://foro.demo.test", "Social", "", false, null, now.minusDays(90)));
        l.add(entry("Streaming Luna", "marta.ruiz", "123456", "https://luna.demo.test", "Ocio", "Plan familiar.", false, null, now.minusDays(30)));
        l.add(entry("Nube Personal", "marta", "pXq-82-Rene-Lodo-Tinta", "https://nube.demo.test", "Trabajo", "", false, "MFRGGZDFMZTWQ2LK", now.minusDays(5)));
        l.add(entry("Gestor de Tareas", "m.ruiz", "u7!Wc0@9mYe2#Hs5", "https://tareas.demo.test", "Trabajo", "", false, null, now.minusDays(20)));
        l.add(entry("Biblioteca Municipal", "marta.ruiz", "libro", "https://biblio.demo.test", "Ocio", "", false, null, now.minusDays(800)));
        l.add(entry("Wifi de casa", "", "Montaña-Sol-Rio-4821", "", "Hogar", "Router del salón.", false, null, now.minusDays(200)));
        l.add(entry("Seguro del hogar", "mruiz", "Verano2020", "https://seguros.demo.test", "Finanzas", "", false, null, now.minusDays(60)));
        return l;
    }

    private static PasswordEntry entry(String title, String user, String pwd, String url, String cat, String notes, boolean fav,
                                       String totp, LocalDateTime modified) {
        PasswordEntry e = new PasswordEntry(title, user, pwd, url, notes);
        e.setCategory(cat);
        e.setFavorite(fav);
        if (totp != null) e.setTotpSecret(totp);
        e.setCreated(modified.minusDays(30));
        e.setModified(modified);
        return e;
    }

    // ---- utilidades ----

    private static void shot(Window w, String name) throws Exception {
        BufferedImage[] img = new BufferedImage[1];
        onEdt(() -> {
            w.validate();
            BufferedImage i = new BufferedImage(w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = i.createGraphics();
            w.paint(g);
            g.dispose();
            img[0] = i;
        });
        File f = out.resolve(name + ".png").toFile();
        ImageIO.write(img[0], "png", f);
        System.out.println("captura " + f + " " + img[0].getWidth() + "x" + img[0].getHeight());
    }

    private static void onEdt(Runnable r) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeAndWait(r);
    }

    private static void pause(int ms) throws Exception {
        Thread.sleep(ms);
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static void renameHost(Container root, String host) {
        for (Component c : root.getComponents()) {
            if (c instanceof JTextField tf && !(c instanceof JPasswordField) && tf.getText().equals(host)) tf.setText("Portátil de Marta");
            if (c instanceof Container k) renameHost(k, host);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Component> T find(Container root, Class<?> type) {
        if (root == null) return null;
        for (Component c : root.getComponents()) {
            if (type != null && type.isInstance(c)) return (T) c;
            if (c instanceof Container k) {
                T r = find(k, type);
                if (r != null) return r;
            }
        }
        return null;
    }
}
