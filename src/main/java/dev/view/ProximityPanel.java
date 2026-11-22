package dev.view;

import com.formdev.flatlaf.FlatClientProperties;
import dev.sync.ProximityService;
import dev.sync.SyncService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Dispositivos cercanos: vinculados y descubiertos en la red local, sincronización directa, vinculación con código de
 * seguridad y desvinculación. Funciona SIN servidor de sincronización. Se refresca sola mientras está visible.
 */
final class ProximityPanel extends JPanel {
    private final Component owner;
    private final SyncService sync;
    private final Runnable onDataChanged;
    private final DeviceList devices = new DeviceList("Dispositivos cercanos y vinculados");
    private final JLabel status = new JLabel(" ");
    private final JProgressBar progress = new JProgressBar();
    private final JButton syncOne = Ui.primary("Sincronizar con este", Icons.onFill("refresh", 16), e -> {});
    private final JButton link = Ui.secondary("Vincular por proximidad…");
    private final JButton allow = Ui.secondary("Permitir vinculación");
    private final JButton unlink = Ui.secondary("Desvincular");
    private final JButton retry = Ui.secondary("Reintentar");
    private final Banner banner = new Banner();
    private volatile List<ProximityService.Row> rows = List.of();
    private final Timer timer;

    ProximityPanel(Component owner, SyncService sync, Runnable onDataChanged) {
        super(new BorderLayout(0, 10));
        this.owner = owner; this.sync = sync; this.onDataChanged = onDataChanged;
        setOpaque(false);
        JLabel help = new JLabel("<html><body style='width:560px'>Sincroniza directamente por la red local, sin servidor. Para vincular un equipo nuevo: en uno pulsa "
                + "«Permitir vinculación» y en el otro «Vincular por proximidad…»; compara el código de 6 cifras en las dos pantallas. "
                + "Puertos: 47890/tcp y 47891/udp.</body></html>");
        help.putClientProperty(FlatClientProperties.STYLE, "foreground: $App.muted");
        JPanel head = new JPanel(new BorderLayout(0, 6));
        head.setOpaque(false);
        head.add(Ui.section(0, "Dispositivos cercanos"), BorderLayout.NORTH);
        head.add(help, BorderLayout.CENTER);
        head.add(banner, BorderLayout.SOUTH);
        add(head, BorderLayout.NORTH);

        JScrollPane sp = new JScrollPane(devices);
        sp.setBorder(null);
        sp.getViewport().setOpaque(false);
        sp.setOpaque(false);
        devices.setOpaque(false);
        devices.setEmptyText("Buscando dispositivos CipherKey en la red local…");
        add(sp, BorderLayout.CENTER);

        progress.setIndeterminate(true);
        progress.setVisible(false);
        
        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.setOpaque(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(retry); buttons.add(unlink); buttons.add(allow); buttons.add(link); buttons.add(syncOne);
        status.setFont(Fonts.regular(12));
        south.add(status, BorderLayout.NORTH);
        south.add(progress, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        syncOne.addActionListener(e -> syncSelected());
        link.addActionListener(e -> linkDialog());
        allow.addActionListener(e -> allowPairing());
        retry.addActionListener(e -> { setStatus("Reintentando…", false); work("Reintentando…", () -> sync.retryProximity(), x -> { }); });
        unlink.addActionListener(e -> unlinkSelected());
        devices.addListSelectionListener(e -> updateButtons());
        updateButtons();

        timer = new Timer(3000, e -> refresh());
        timer.setInitialDelay(100);
        timer.start();
    }

    @Override public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    /** Línea de estado: los avisos llevan icono de alerta además del color. */
    private void setStatus(String text, boolean problem) {
        status.setText(text);
        status.setIcon(problem ? Icons.ink("alert", 14) : null); // aviso = icono + texto en tinta, nunca solo color
        status.setIconTextGap(8);
        status.setFont(problem ? Fonts.bold(12) : Fonts.regular(12));
        status.setForeground(problem ? Draw.p().ink() : Draw.p().dim());
    }

    /** Aviso de «vinculación permitida» con la dirección para el otro equipo. */
    private static final class Banner extends JComponent {
        private String text;

        void show(String text) {
            this.text = text;
            setPreferredSize(new Dimension(10, text == null ? 0 : 40));
            setVisible(text != null);
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (text == null) return;
            Graphics2D g2 = Draw.quality(g);
            Palette p = Draw.p();
            Draw.box(g2, p.fill(), null, 0, 4, getWidth(), getHeight() - 8, Draw.CUT_SM);
            g2.setFont(Draw.label());
            g2.setColor(p.onFill());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(Draw.ellipsize(text, fm, getWidth() - 28), 16, Draw.baseline(fm, 0, getHeight()));
            g2.dispose();
        }
    }

    private ProximityService prox() { return sync.proximity(); }

    private ProximityService.Row selected() {
        int i = devices.getSelectedIndex();
        return i < 0 || i >= rows.size() ? null : rows.get(i);
    }

    private void updateButtons() {
        ProximityService.Row r = selected();
        boolean has = prox() != null;
        syncOne.setEnabled(has && r != null && r.linked() && !r.address().isEmpty());
        unlink.setEnabled(has && r != null && r.linked());
        link.setEnabled(has);
        allow.setEnabled(has);
        retry.setVisible(!has && sync.proximityError() != null);
        if (!has && !busy) {
            String e = sync.proximityError();
            setStatus(e != null ? e : "La sincronización por proximidad se está iniciando…", e != null);
        }
    }

    void refresh() {
        ProximityService p = prox();
        if (p == null) { updateButtons(); return; }
        SwingWorker<List<ProximityService.Row>, Void> w = new SwingWorker<>() {
            protected List<ProximityService.Row> doInBackground() throws Exception {
                if (sync.status().configured() && sync.devicesAge() > 30_000) {
                    try { sync.devices(); } catch (Exception ignored) { /* sin servidor: se muestran los vinculados y los cercanos */ }
                }
                p.setServerDevices(sync.cachedDevices());
                return p.rows();
            }
            protected void done() {
                try {
                    rows = get();
                    ProximityService.Row keep = selected();
                    String keepId = keep == null ? null : keep.deviceId();
                    DateFormat f = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
                    Palette pal = Draw.p();
                    List<DeviceList.Item> items = new java.util.ArrayList<>();
                    int sel = -1, i = 0;
                    for (ProximityService.Row r : rows) {
                        List<Chips.Spec> chips = new java.util.ArrayList<>();
                        switch (r.state()) {
                            case NEARBY -> chips.add(Chips.Spec.inverted(Draw.G_OK + " Cercano"));
                            case ONLINE -> chips.add(Chips.Spec.ok("En línea"));
                            case OFFLINE -> chips.add(new Chips.Spec("Desconectado", pal.muted(), pal.lineHigh(), null));
                        }
                        if (r.linked()) chips.add(Chips.Spec.plain("Vinculado", pal.dim()));
                        String meta = (r.address().isEmpty() ? "sin dirección" : r.address());
                        items.add(new DeviceList.Item(r.linked() ? "shield-check" : "smartphone", r.name(), meta, chips,
                                r.lastSyncMillis() == 0 ? "sin sincronizar" : "sincronizado " + f.format(new Date(r.lastSyncMillis()))));
                        if (r.deviceId().equals(keepId)) sel = i;
                        i++;
                    }
                    devices.setItems(items);
                    if (sel >= 0) devices.setSelectedIndex(sel);
                    String err = p.lastError();
                    String disc = p.discoveryError();
                    banner.show(p.pairingOpen() ? "VINCULACIÓN PERMITIDA • el otro equipo ya puede conectarse a "
                            + java.util.Objects.requireNonNullElse(localAddress(), "esta IP") + ":" + p.port() : null);
                    if (!busy) {
                        if (err != null) setStatus(err, true);
                        else if (disc != null) setStatus(disc, true);
                        else setStatus(rows.isEmpty() ? "Buscando dispositivos CipherKey en la red local…" : " ", false);
                    }
                    updateButtons();
                } catch (Exception ignored) { }
            }
        };
        w.execute();
    }

    private volatile boolean busy;

    private <T> void work(String label, Callable<T> task, java.util.function.Consumer<T> ok) {
        busy = true;
        setStatus(label, false);
        progress.setVisible(true);
        setEnabledAll(false);
        SwingWorker<T, Void> w = new SwingWorker<>() {
            protected T doInBackground() throws Exception { return task.call(); }
            protected void done() {
                busy = false;
                progress.setVisible(false);
                setEnabledAll(true);
                try {
                    ok.accept(get());
                } catch (Exception e) {
                    Throwable c = e.getCause() != null ? e.getCause() : e;
                    setStatus(c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage(), true);
                }
                refresh();
            }
        };
        w.execute();
    }

    private void setEnabledAll(boolean on) {
        for (JButton b : List.of(syncOne, link, allow, unlink, retry)) b.setEnabled(on);
        if (on) updateButtons();
    }

    private void syncSelected() {
        ProximityService.Row r = selected();
        if (r == null || r.address().isEmpty()) return;
        work("Sincronizando con «" + r.name() + "» por la red local…", () -> prox().syncWith(r.address()), res -> {
            setStatus("Sincronizado con «" + res.peerName() + "»: " + res.received() + " recibidos, " + res.sent() + " enviados"
                    + (res.conflicts() > 0 ? ", " + res.conflicts() + " conflicto(s)" : "") + ".", false);
            onDataChanged.run();
        });
    }

    private void linkDialog() {
        ProximityService.Row r = selected();
        String initial = r != null && !r.address().isEmpty() ? r.address() : "";
        String addr = (String) JOptionPane.showInputDialog(owner,
                "Dirección del otro dispositivo (IP:puerto). Si lo has seleccionado en la lista ya está rellena.\nEn el otro dispositivo debe estar abierta «Permitir vinculación».\n"
                        + "Verás un código de 6 dígitos en ambos: compáralos antes de aceptar.",
                "Vincular dispositivo cercano", JOptionPane.PLAIN_MESSAGE, null, null, initial);
        if (addr == null || addr.isBlank()) return;
        work("Conectando y esperando la confirmación del código…", () -> prox().pairWith(addr), res -> {
            setStatus("Vinculado con «" + res.peerName() + "»: " + res.received() + " recibidas, " + res.sent() + " enviadas"
                    + (res.conflicts() > 0 ? ", " + res.conflicts() + " conflicto(s) por resolver" : "") + ".", false);
            onDataChanged.run();
        });
    }

    private void allowPairing() {
        prox().allowPairing(120_000);
        Toast.show(owner, "Durante 2 minutos se acepta la vinculación de un equipo nuevo");
        refresh();
    }

    private static String localAddress() {
        try { return java.net.InetAddress.getLocalHost().getHostAddress(); } catch (Exception e) { return null; }
    }

    private void unlinkSelected() {
        ProximityService.Row r = selected();
        if (r == null || !r.linked()) return;
        if (JOptionPane.showConfirmDialog(owner, "¿Desvincular «" + r.name() + "» de la sincronización directa?\n"
                + "Dejará de sincronizar por proximidad con este equipo hasta que lo vuelvas a vincular (el servidor no se toca).",
                "Desvincular", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        work("Desvinculando…", () -> { prox().unlink(r.deviceId()); return null; }, x -> setStatus("«" + r.name() + "» desvinculado.", false));
    }
}
