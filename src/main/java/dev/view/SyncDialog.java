package dev.view;

import com.formdev.flatlaf.FlatClientProperties;
import dev.cipherkey.core.model.Record;
import dev.cipherkey.core.sync.PairingCode;
import dev.cipherkey.core.sync.ServerClient;
import dev.security.SecureClipboard;
import dev.sync.SyncService;

import javax.swing.*;
import java.awt.*;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Sincronización: dispositivos cercanos (proximidad, siempre disponible y sin servidor), servidor opcional
 * (configurar, emparejar, revocar) y resolución de conflictos.
 */
public class SyncDialog extends JDialog {
    private final SyncService sync;
    private final Runnable onDataChanged;
    private final JPanel root = new JPanel(new BorderLayout());

    public SyncDialog(Frame parent, SyncService sync, Runnable onDataChanged) {
        super(parent, "Sincronización", true);
        Ui.configureDialog(this);
        setResizable(true);
        this.sync = sync;
        this.onDataChanged = onDataChanged;
        setContentPane(root);
        rebuild();
        setMinimumSize(new Dimension(700, 600));
        setPreferredSize(new Dimension(800, 780));
        pack();
        setLocationRelativeTo(parent);
    }

    private static JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Fonts.bold(24));
        return l;
    }

    private void rebuild() {
        root.removeAll();
        JScrollPane sp = new JScrollPane(sync.status().configured() ? configuredView() : setupView());
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        root.add(sp, BorderLayout.CENTER);
        root.revalidate();
        root.repaint();
    }

    // ------------------------------------------------------------ sin configurar

    private JComponent setupView() {
        JTextField url = new JTextField("https://", 28);
        JTextField name = new JTextField(hostName(), 28);
        JPasswordField admin = Ui.passwordField(28);
        JTextField code = new JTextField(28);

        VBox body = new VBox();
        body.setBorder(BorderFactory.createEmptyBorder(22, 26, 20, 26));
        body.row(heading("Sincronización"), 0);
        body.row(Ui.muted("Entre tus dispositivos en la misma red no hace falta ningún servidor."), 4);
        body.row(new ProximityPanel(this, sync, onDataChanged), 10);
        body.row(new JSeparator(), 16);
        body.row(Ui.section(1, "Servidor de sincronización (opcional)"), 12);
        body.row(Ui.muted("Para sincronizar fuera de tu red. Tus contraseñas se cifran en este equipo; el servidor solo guarda datos cifrados."), 4);
        body.row(Ui.labeled("Dirección del servidor (HTTPS)", url), 12);
        body.row(Ui.labeled("Nombre de este dispositivo", name), 10);
        body.row(Ui.labeled("Token de administrador del servidor (si lo exige)", admin), 10);
        JButton create = Ui.primary("Activar con una bóveda nueva", null, e -> {
            String token = new String(admin.getPassword());
            run("Activando…", () -> { sync.enableNew(url.getText().trim(), token, name.getText().trim()); return null; },
                    r -> { onDataChanged.run(); rebuild(); });
        });
        body.row(Ui.buttonBar(create), 12);
        body.row(new JSeparator(), 16);
        body.row(Ui.section(2, "¿Ya sincronizas otro dispositivo?"), 12);
        body.row(Ui.labeled("Código de emparejamiento", code), 8);
        JButton pair = new JButton("Vincular con este código");
        pair.addActionListener(e -> run("Vinculando…", () -> { sync.pair(code.getText(), name.getText().trim()); return null; },
                r -> { onDataChanged.run(); rebuild(); }));
        body.row(Ui.buttonBar(pair), 12);
        JButton close = new JButton("Cerrar");
        close.addActionListener(e -> dispose());
        body.row(Ui.buttonBar(close), 14);
        return body;
    }

    // ------------------------------------------------------------ configurada

    private JComponent configuredView() {
        SyncService.Status st = sync.status();
        VBox body = new VBox();
        body.setBorder(BorderFactory.createEmptyBorder(22, 26, 20, 26));
        body.row(heading("Sincronización"), 0);
        String last = st.lastSyncMillis() == 0 ? "aún no" : DateFormat.getDateTimeInstance().format(new Date(st.lastSyncMillis()));
        String route = sync.lastRoute().isEmpty() ? "" : "  •  Vía: " + sync.lastRoute();
        body.row(Ui.muted("Última sincronización: " + last + "  •  Pendientes: " + st.pending() + "  •  Conflictos: " + st.conflicts() + route), 4);
        if (st.error() != null) {
            JLabel err = new JLabel(st.error(), Icons.colored("alert", 14, () -> Draw.p().error()), SwingConstants.LEADING);
            err.setIconTextGap(8);
            err.putClientProperty(FlatClientProperties.STYLE, "foreground: $App.danger");
            body.row(err, 4);
        }

        JButton now = Ui.primary("Sincronizar ahora", Icons.onAccent("refresh", 16), e -> run("Sincronizando…",
                () -> sync.syncNow(), r -> { onDataChanged.run(); rebuild(); }));
        JButton pairCode = new JButton("Vincular otro dispositivo…");
        pairCode.addActionListener(e -> run("Generando código…", sync::newPairingCode, this::showPairingCode));
        JButton off = new JButton("Desactivar");
        off.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Este dispositivo dejará de sincronizar. Tus datos locales se conservan\n"
                    + "y no se borra nada del servidor. ¿Continuar?", "Desactivar", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                run("Desactivando…", () -> { sync.disable(); return null; }, r -> rebuild());
            }
        });
        body.row(Ui.buttonBar(off, pairCode, now), 14);

        // dispositivos
        body.row(Ui.section(1, "Dispositivos del servidor"), 18);
        DeviceList table = new DeviceList("Dispositivos del servidor");
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        table.setOpaque(false);
        sp.setPreferredSize(new Dimension(500, 150));
        body.row(sp, 8);
        List<ServerClient.DeviceInfo>[] devices = new List[]{List.of()};
        String[] self = {""};
        JButton revoke = new JButton("Revocar dispositivo seleccionado");
        revoke.addActionListener(e -> {
            int i = table.getSelectedIndex();
            if (i < 0) return;
            ServerClient.DeviceInfo d = devices[0].get(i);
            if (d.revoked()) return;
            String warn = d.deviceId().equals(self[0]) ? "Es ESTE dispositivo: dejará de sincronizar." : "Dejará de poder leer y escribir en el servidor.";
            if (JOptionPane.showConfirmDialog(this, "¿Revocar «" + d.name() + "»?\n" + warn, "Revocar", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                run("Revocando…", () -> { sync.revoke(d.deviceId()); return null; }, r -> rebuild());
            }
        });
        body.row(Ui.buttonBar(revoke), 4);
        loadDevices(table, devices, self);
        body.row(new ProximityPanel(this, sync, onDataChanged), 22);

        // conflictos
        List<Record> conflicts = sync.conflicts();
        if (!conflicts.isEmpty()) {
            body.row(Ui.section(3, "Conflictos por resolver (" + conflicts.size() + ")"), 22);
            JList<String> list = new JList<>(conflicts.stream().map(r -> r.title.isEmpty() ? "(sin título)" : r.title).toArray(String[]::new));
            list.setVisibleRowCount(4);
            JTextArea diff = new JTextArea(6, 40);
            diff.setEditable(false);
            diff.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            list.addListSelectionListener(e -> {
                int i = list.getSelectedIndex();
                if (i >= 0) diff.setText(describe(conflicts.get(i)));
            });
            body.row(new JScrollPane(list), 6);
            body.row(new JScrollPane(diff), 6);
            JButton mine = new JButton("Quedarme con la mía");
            JButton other = new JButton("Usar la otra");
            JButton both = new JButton("Conservar ambas");
            mine.addActionListener(e -> resolve(list, conflicts, SyncService.Choice.KEEP_MINE));
            other.addActionListener(e -> resolve(list, conflicts, SyncService.Choice.TAKE_OTHER));
            both.addActionListener(e -> resolve(list, conflicts, SyncService.Choice.KEEP_BOTH));
            body.row(Ui.buttonBar(mine, other, both), 6);
        }
        JButton close = new JButton("Cerrar");
        close.addActionListener(e -> dispose());
        body.row(Ui.buttonBar(close), 14);
        return body;
    }

    private void loadDevices(DeviceList model, List<ServerClient.DeviceInfo>[] holder, String[] self) {
        SwingWorker<Object[], Void> w = new SwingWorker<>() {
            protected Object[] doInBackground() throws Exception { return new Object[]{sync.devices(), sync.thisDeviceId()}; }
            @SuppressWarnings("unchecked")
            protected void done() {
                try {
                    Object[] r = get();
                    holder[0] = (List<ServerClient.DeviceInfo>) r[0];
                    self[0] = (String) r[1];
                    DateFormat f = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
                    Palette pal = Draw.p();
                    List<DeviceList.Item> items = new java.util.ArrayList<>();
                    for (ServerClient.DeviceInfo d : holder[0]) {
                        List<Chips.Spec> chips = new java.util.ArrayList<>();
                        if (d.deviceId().equals(self[0])) chips.add(new Chips.Spec("Este equipo", pal.onAccent(), pal.accent(), pal.accent()));
                        chips.add(d.revoked() ? new Chips.Spec("Revocado", pal.error(), pal.error(), null) : new Chips.Spec("Activo", pal.ok(), pal.ok(), null));
                        items.add(new DeviceList.Item(d.deviceId().equals(self[0]) ? "key" : "smartphone", d.name(), "alta " + f.format(new Date(d.createdAt())),
                                chips, d.lastSeen() == 0 ? "sin actividad" : "visto " + f.format(new Date(d.lastSeen()))));
                    }
                    model.setItems(items);
                } catch (Exception e) {
                    model.setEmptyText("No se pudo consultar el servidor");
                }
            }
        };
        w.execute();
    }

    private void resolve(JList<String> list, List<Record> conflicts, SyncService.Choice choice) {
        int i = list.getSelectedIndex();
        if (i < 0) { JOptionPane.showMessageDialog(this, "Selecciona un conflicto."); return; }
        String uuid = conflicts.get(i).uuid;
        run("Resolviendo…", () -> { sync.resolve(uuid, choice); return null; }, r -> { onDataChanged.run(); rebuild(); });
    }

    private static String describe(Record r) {
        StringBuilder sb = new StringBuilder();
        if (r.conflict.deleted) {
            return "La otra versión ELIMINÓ este registro; tú lo has editado.\n\nTuya:\n  " + r.title + " / " + r.username;
        }
        if (r.deleted) return "Tú lo eliminaste; en la otra versión se editó.";
        var mine = r.contentFields();
        var theirs = r.conflict.contentFields();
        for (String k : mine.keySet()) {
            if (!mine.get(k).equals(theirs.get(k))) {
                boolean secret = k.equals("password") || k.equals("totpSecret");
                sb.append(k).append(":\n  mía:   ").append(secret ? "••••••••" : mine.get(k))
                        .append("\n  otra:  ").append(secret ? "••••••••" : theirs.get(k)).append("\n");
            }
        }
        return sb.length() == 0 ? "Sin diferencias visibles." : sb.toString();
    }

    private void showPairingCode(PairingCode code) {
        JTextArea area = new JTextArea(code.encode(), 5, 44);
        area.setLineWrap(true);
        area.setEditable(false);
        Object[] msg = {"Escribe o pega este código en el otro dispositivo (caduca en 10 minutos, sirve una vez).\n"
                + "Contiene la clave de sincronización: no lo compartas ni lo guardes.", new JScrollPane(area)};
        Object[] opts = {"Copiar (se borra a los 30 s)", "Cerrar"};
        if (JOptionPane.showOptionDialog(this, msg, "Vincular otro dispositivo", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, opts, opts[1]) == 0) {
            SecureClipboard.copyToClipboard(code.encode());
        }
    }

    // ------------------------------------------------------------ utilidades

    private <T> void run(String label, Callable<T> task, Consumer<T> ok) {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        SwingWorker<T, Void> w = new SwingWorker<>() {
            protected T doInBackground() throws Exception { return task.call(); }
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    ok.accept(get());
                } catch (Exception e) {
                    Throwable c = e.getCause() != null ? e.getCause() : e;
                    JOptionPane.showMessageDialog(SyncDialog.this, label.replace("…", "") + " falló: " + c.getMessage(), "Sincronización", JOptionPane.ERROR_MESSAGE);
                    rebuild();
                }
            }
        };
        w.execute();
    }

    private static String hostName() {
        try { return java.net.InetAddress.getLocalHost().getHostName(); } catch (Exception e) { return "Escritorio"; }
    }
}
