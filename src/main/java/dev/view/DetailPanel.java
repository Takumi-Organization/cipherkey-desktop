package dev.view;

import com.formdev.flatlaf.FlatClientProperties;
import dev.model.PasswordEntry;
import dev.security.Totp;
import dev.security.VaultHealth;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Panel derecho: ficha de la entrada en secciones (credenciales, 2FA, sitio, notas, registro) y sus acciones. */
public class DetailPanel extends JPanel {
    /** Acciones que el panel delega en la ventana principal. */
    public interface Actions {
        void edit();
        void delete();
        void toggleFavorite();
        void copyUsername();
        void copyPassword();
        void openUrl();
        void copyTotp();
        void showHistory();
    }

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.forLanguageTag("es"));

    private final Actions actions;
    private final CardLayout cards = new CardLayout();

    private final JLabel avatar = new JLabel();
    private final JLabel title = new JLabel();
    private final JLabel subtitle = Ui.muted(" ");
    private final JButton favorite = Ui.iconButton(Icons.get("star", 18), "Marcar como favorita", e -> {});
    private final Chips chips = new Chips();
    private final JLabel username = new JLabel();
    private final PasswordView password = new PasswordView(15, false);
    private final JButton reveal = Ui.iconButton(Icons.get("eye"), "Mostrar contraseña", e -> {});
    private final StrengthMeter meter = new StrengthMeter();
    private final JButton urlLink = new JButton();
    private final JTextArea notes = new JTextArea();
    private final JLabel created = new JLabel(), modified = new JLabel();
    private final JButton historyBtn = new JButton("Historial de contraseñas", Icons.get("clock"));

    private final Ring totpRing = new Ring(52, 4, 15, 9);
    private final JLabel totpCode = new JLabel(" ");
    private final JPanel totpSection = new JPanel(new BorderLayout());
    private final JPanel urlSection = new JPanel(new BorderLayout()), notesSection = new JPanel(new BorderLayout());
    private final Timer totpTimer;
    private Totp totp;

    private boolean revealed;
    private PasswordEntry current;

    public DetailPanel(Actions actions) {
        super(new BorderLayout());
        this.actions = actions;
        setLayout(cards);
        setPreferredSize(new Dimension(390, 0));
        totpTimer = new Timer(Motion.enabled() ? 100 : 1000, e -> tickTotp());

        add(Art.panel(Art.Kind.SELECT, "00 // Selecciona una entrada"), "empty");
        add(buildContent(), "content");
        cards.show(this, "empty");
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setBackground(UIManager.getColor("App.card.background"));
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        g.setColor(Draw.p().line1());
        g.fillRect(0, 0, 1, getHeight()); // filete que separa de la lista
    }

    private JComponent buildContent() {
        VBox.Scrolling col = new VBox.Scrolling();
        col.setBorder(BorderFactory.createEmptyBorder(22, 22, 20, 22));

        title.setFont(Fonts.bold(20));
        favorite.addActionListener(e -> actions.toggleFavorite());
        JPanel titles = new JPanel(new GridLayout(0, 1, 0, 3));
        titles.setOpaque(false);
        titles.add(title);
        titles.add(subtitle);
        JPanel titlesWrap = new JPanel(new GridBagLayout());
        titlesWrap.setOpaque(false);
        titlesWrap.add(titles, new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.WEST,
                GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setOpaque(false);
        header.add(avatar, BorderLayout.WEST);
        header.add(titlesWrap, BorderLayout.CENTER);
        header.add(favorite, BorderLayout.EAST);

        reveal.addActionListener(e -> {
            revealed = !revealed;
            renderPassword();
        });
        username.setFont(Fonts.regular(14));

        urlLink.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        urlLink.putClientProperty(FlatClientProperties.STYLE, "foreground: $App.accent; margin: 0,0,0,0");
        urlLink.setHorizontalAlignment(SwingConstants.LEFT);
        urlLink.addActionListener(e -> actions.openUrl());

        notes.setEditable(false);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setOpaque(false);
        notes.setBorder(null);
        notes.setFont(Fonts.regular(13));

        historyBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        historyBtn.putClientProperty(FlatClientProperties.STYLE, "margin: 4,0,4,0");
        historyBtn.setHorizontalAlignment(SwingConstants.LEFT);
        historyBtn.addActionListener(e -> actions.showHistory());

        JButton edit = Ui.primary("Editar", Icons.onAccent("edit", 16), e -> actions.edit());
        edit.setToolTipText("Ctrl+E");
        JButton delete = Ui.danger("Eliminar", Icons.colored("trash", 16, () -> UIManager.getColor("App.danger")), e -> actions.delete());
        JPanel buttons = new JPanel(new GridLayout(1, 2, 10, 0));
        buttons.setOpaque(false);
        buttons.add(edit);
        buttons.add(delete);

        // 2FA: anillo + código grande
        totpCode.setFont(Fonts.bold(26));
        JPanel totpBody = new JPanel(new BorderLayout(14, 0));
        totpBody.setOpaque(false);
        totpBody.add(totpRing, BorderLayout.WEST);
        totpBody.add(totpCode, BorderLayout.CENTER);
        totpBody.add(new CopyButton("Copiar código 2FA", actions::copyTotp), BorderLayout.EAST);
        totpSection.setOpaque(false);
        totpSection.add(box(null, totpBody), BorderLayout.CENTER);

        urlSection.setOpaque(false);
        urlSection.add(field("Sitio web", urlLink, Ui.iconButton(Icons.get("external-link", 16), "Abrir en el navegador", e -> actions.openUrl())),
                BorderLayout.CENTER);
        notesSection.setOpaque(false);
        notesSection.add(box(null, notes), BorderLayout.CENTER);

        col.row(header, 0);
        col.row(chips, 12);
        col.row(Ui.section(0, "Credenciales"), 22);
        col.row(field("Usuario", username, new CopyButton("Copiar usuario (Ctrl+U)", actions::copyUsername)), 8);
        col.row(field("Contraseña", password, reveal, new CopyButton("Copiar contraseña (Ctrl+B)", actions::copyPassword)), 8);
        col.row(meter, 10);
        col.row(sectionWith(1, "Código 2FA", totpSection), 20);
        col.row(sectionWith(2, "Sitio", urlSection), 20);
        col.row(sectionWith(3, "Notas", notesSection), 20);
        col.row(Ui.section(4, "Registro"), 20);
        col.row(lines(), 8);
        col.row(historyBtn, 2);
        col.glue();
        col.row(buttons, 18);

        JScrollPane scroll = new JScrollPane(col, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        return scroll;
    }

    private JPanel lines() {
        JPanel p = new JPanel(new GridLayout(0, 1, 0, 4));
        p.setOpaque(false);
        created.putClientProperty(FlatClientProperties.STYLE, "foreground: $App.muted");
        modified.putClientProperty(FlatClientProperties.STYLE, "foreground: $App.muted");
        p.add(created);
        p.add(modified);
        return p;
    }

    private static JPanel sectionWith(int n, String name, JComponent body) {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setOpaque(false);
        p.add(Ui.section(n, name), BorderLayout.NORTH);
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    /** Marco plano de 1 px con relleno de papel (los datos van en «cajas» sobre el panel elevado). */
    private static JPanel box(String label, JComponent value, JComponent... actionButtons) {
        JPanel box = new JPanel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Draw.quality(g);
                Draw.box(g2, Draw.p().paper(), Draw.p().line2(), 0, 0, getWidth(), getHeight(), Draw.RADIUS);
                g2.dispose();
            }
        };
        box.setOpaque(false);
        box.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 8));
        JPanel center = new JPanel(new BorderLayout(0, 4));
        center.setOpaque(false);
        if (label != null) center.add(Ui.eyebrow(label), BorderLayout.NORTH);
        JPanel valueWrap = new JPanel(new BorderLayout());
        valueWrap.setOpaque(false);
        valueWrap.add(value, BorderLayout.CENTER);
        center.add(valueWrap, BorderLayout.CENTER);
        box.add(center, BorderLayout.CENTER);
        if (actionButtons.length > 0) {
            JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
            btns.setOpaque(false);
            for (JComponent b : actionButtons) btns.add(b);
            JPanel btnWrap = new JPanel(new GridBagLayout());
            btnWrap.setOpaque(false);
            btnWrap.add(btns);
            box.add(btnWrap, BorderLayout.EAST);
        }
        return box;
    }

    private static JPanel field(String label, JComponent value, JComponent... actionButtons) {
        return box(label, value, actionButtons);
    }

    public void show(PasswordEntry entry, VaultHealth.EntryHealth health) {
        current = entry;
        revealed = false;
        totpTimer.stop();
        totp = null;
        if (entry == null) {
            cards.show(this, "empty");
            return;
        }
        avatar.setIcon(new Icons.Avatar(entry.getTitle(), entry.getUrl(), 56));
        title.setText(entry.getTitle());
        String site = Sites.domain(entry.getUrl());
        String cat = entry.getCategory();
        subtitle.setText(site.isEmpty() ? (cat == null || cat.isBlank() ? "Sin categoría" : cat) : site);
        favorite.setIcon(entry.isFavorite()
                ? Icons.colored("star-filled", 18, () -> UIManager.getColor("App.warning"))
                : Icons.get("star", 18));
        favorite.setToolTipText(entry.isFavorite() ? "Quitar de favoritas" : "Marcar como favorita");
        favorite.getAccessibleContext().setAccessibleName(favorite.getToolTipText());

        List<Chips.Spec> specs = new ArrayList<>();
        Palette p = Draw.p();
        if (cat != null && !cat.isBlank()) specs.add(new Chips.Spec(cat, p.dim(), p.line2(), null));
        boolean hasTotp = entry.getTotpSecret() != null && !entry.getTotpSecret().isBlank();
        if (hasTotp) specs.add(new Chips.Spec("2FA", p.secondary(), p.line2(), null));
        if (health != null) {
            if (health.has(VaultHealth.Flag.WEAK)) specs.add(new Chips.Spec("Débil", p.error(), p.error(), null));
            if (health.has(VaultHealth.Flag.REUSED)) specs.add(new Chips.Spec("Repetida", p.warn(), p.warn(), null));
            if (health.has(VaultHealth.Flag.OLD)) specs.add(new Chips.Spec("Antigua", p.muted(), p.line3(), null));
        }
        chips.set(specs);

        username.setText(orDash(entry.getUsername()));
        meter.update(entry.getPassword());

        boolean hasUrl = entry.getUrl() != null && !entry.getUrl().isBlank();
        urlLink.setText(hasUrl ? entry.getUrl() : "—");
        urlSection.getParent().setVisible(hasUrl);
        boolean hasNotes = entry.getNotes() != null && !entry.getNotes().isBlank();
        notes.setText(hasNotes ? entry.getNotes() : "");
        notesSection.getParent().setVisible(hasNotes);

        LocalDateTime now = LocalDateTime.now();
        created.setText("Creada     " + DATE_TIME.format(entry.getCreated()));
        modified.setText("Modificada " + DATE_TIME.format(entry.getModified()) + "  •  " + When.relative(entry.getModified(), now));
        int historySize = entry.getHistory().size();
        historyBtn.setVisible(historySize > 0);
        historyBtn.setText("Historial de contraseñas (" + historySize + ")");

        totpSection.getParent().setVisible(hasTotp);
        if (hasTotp) {
            try {
                totp = Totp.parse(entry.getTotpSecret());
                tickTotp();
                totpTimer.setDelay(Motion.enabled() ? 100 : 1000);
                totpTimer.start();
            } catch (IllegalArgumentException ex) {
                totpCode.setText("Clave no válida");
                totpCode.setFont(Fonts.bold(14));
                totpRing.setValue(0);
                totpRing.setCenter("", "");
            }
        }
        renderPassword();
        cards.show(this, "content");
        revalidate();
        repaint();
    }

    private void tickTotp() {
        if (totp == null) return;
        Instant now = Instant.now();
        String code = totp.code(now);
        int mid = code.length() / 2;
        totpCode.setFont(Fonts.bold(26));
        totpCode.setText(code.substring(0, mid) + " " + code.substring(mid));
        long periodMs = totp.period() * 1000L;
        double left = 1 - (now.toEpochMilli() % periodMs) / (double) periodMs;
        int seconds = totp.secondsRemaining(now);
        totpRing.setValue(Motion.enabled() ? left : seconds / (double) totp.period());
        totpRing.setCenter(String.valueOf(seconds), "");
        totpRing.setColor(seconds <= 5 ? Draw.p().error() : Draw.p().accent());
        totpCode.setForeground(seconds <= 5 ? Draw.p().error() : Draw.p().ink());
    }

    @Override
    public void removeNotify() {
        totpTimer.stop();
        super.removeNotify();
    }

    private void renderPassword() {
        password.setPassword(current == null ? "" : current.getPassword(), revealed);
        reveal.setIcon(Icons.get(revealed ? "eye-off" : "eye"));
        reveal.setToolTipText(revealed ? "Ocultar contraseña" : "Mostrar contraseña");
        reveal.getAccessibleContext().setAccessibleName(reveal.getToolTipText());
    }

    private static String orDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
