package dev.view;

import dev.model.PasswordEntry;
import dev.security.PasswordStrength;
import dev.security.VaultHealth;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * «Salud de la bóveda»: puntuación grande en un anillo, cuatro contadores que llevan a la lista filtrada,
 * distribución de fortaleza, cobertura 2FA y qué arreglar primero. Todo pintado en Java2D, plano.
 */
public class HealthView extends JPanel {
    private final Consumer<String> applyQuery;
    private final Consumer<PasswordEntry> openEntry;
    private final Ring ring = new Ring(190, 14, 60, 10);
    private final JLabel headline = new JLabel();
    private final JLabel detail = Ui.muted(" ");
    private final GradeChip grade = new GradeChip();
    private final Stat weak = new Stat("Débiles", "alert", "is:weak"), reused = new Stat("Repetidas", "repeat", "is:reused"),
            old = new Stat("Antiguas", "clock", "is:old"), empty = new Stat("Sin contraseña", "x", "is:empty");
    private final Distribution distribution = new Distribution();
    private final Coverage coverage = new Coverage();
    private final FixList fixList = new FixList();
    private VaultHealth.Result result;

    public HealthView(Consumer<String> applyQuery, Consumer<PasswordEntry> openEntry) {
        super(new BorderLayout());
        this.applyQuery = applyQuery;
        this.openEntry = openEntry;
        setOpaque(false);

        headline.setFont(Fonts.bold(22));
        JPanel text = new JPanel(new GridLayout(0, 1, 0, 6));
        text.setOpaque(false);
        JPanel gradeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        gradeRow.setOpaque(false);
        gradeRow.add(grade);
        text.add(Ui.section(0, "Salud de la bóveda"));
        text.add(headline);
        text.add(detail);
        text.add(gradeRow);

        JPanel top = new JPanel(new BorderLayout(32, 0));
        top.setOpaque(false);
        top.add(ring, BorderLayout.WEST);
        JPanel topRight = new JPanel(new BorderLayout(0, 18));
        topRight.setOpaque(false);
        topRight.add(text, BorderLayout.NORTH);
        JPanel stats = new JPanel(new GridLayout(1, 4, 10, 0));
        stats.setOpaque(false);
        for (Stat s : List.of(weak, reused, old, empty)) stats.add(s);
        topRight.add(stats, BorderLayout.CENTER);
        top.add(topRight, BorderLayout.CENTER);

        JPanel cols = new JPanel(new GridLayout(1, 2, 18, 0));
        cols.setOpaque(false);
        JPanel left = new JPanel(new BorderLayout(0, 10));
        left.setOpaque(false);
        left.add(Ui.section(1, "Fortaleza"), BorderLayout.NORTH);
        JPanel leftBody = new JPanel(new BorderLayout(0, 14));
        leftBody.setOpaque(false);
        leftBody.add(frame(distribution), BorderLayout.CENTER);
        left.add(leftBody, BorderLayout.CENTER);
        JPanel covWrap = new JPanel(new BorderLayout(0, 10));
        covWrap.setOpaque(false);
        covWrap.add(Ui.section(2, "Cobertura 2FA"), BorderLayout.NORTH);
        covWrap.add(frame(coverage), BorderLayout.CENTER);
        leftBody.add(covWrap, BorderLayout.SOUTH);
        JPanel right = new JPanel(new BorderLayout(0, 10));
        right.setOpaque(false);
        right.add(Ui.section(3, "Qué arreglar primero"), BorderLayout.NORTH);
        right.add(frame(fixList), BorderLayout.CENTER);
        cols.add(left);
        cols.add(right);

        VBox.Scrolling col = new VBox.Scrolling();
        col.setBorder(BorderFactory.createEmptyBorder(26, 30, 26, 30));
        col.row(top, 0);
        col.row(cols, 28);
        JScrollPane sp = new JScrollPane(col, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        add(sp, BorderLayout.CENTER);
    }

    private static JComponent frame(JComponent c) {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Draw.quality(g);
                Draw.box(g2, Draw.p().raised(), Draw.p().line1(), 0, 0, getWidth(), getHeight(), Draw.RADIUS);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    public void update(VaultHealth.Result r, boolean animate) {
        this.result = r;
        Palette p = Draw.p();
        boolean none = r.total() == 0;
        ring.setCenter(none ? "—" : String.valueOf(r.score()), "DE 100");
        ring.setColor(null);
        double target = none ? 0 : r.score() / 100.0;
        if (animate) Motion.run(600, t -> ring.setValue(target * t), null);
        else ring.setValue(target);
        headline.setText(headlineFor(r.grade()));
        detail.setText(none ? "Añade entradas para ver su estado." : r.healthy() + " de " + r.total() + " entradas sin ningún aviso"
                + (r.issues() == 0 ? "." : " • " + r.issues() + (r.issues() == 1 ? " aviso" : " avisos") + " por revisar."));
        grade.set(r.grade(), colorFor(r));
        weak.set(r.weak());
        reused.set(r.reused());
        old.set(r.old());
        empty.set(r.empty());
        distribution.set(r);
        coverage.set(r.withTotp(), r.total());
        fixList.set(r);
        getAccessibleContext().setAccessibleName("Salud de la bóveda: " + (none ? "sin datos" : r.score() + " de 100, " + r.grade().label()));
        repaint();
    }

    private static Color colorFor(VaultHealth.Result r) {
        Palette p = Draw.p();
        return switch (r.grade()) {
            case EXCELLENT, GOOD -> p.ok();
            case FAIR -> p.warn();
            case CRITICAL -> p.error();
            case NONE -> p.faint();
        };
    }

    static String headlineFor(VaultHealth.Grade g) {
        return switch (g) {
            case EXCELLENT -> "Tu bóveda está en forma.";
            case GOOD -> "Buen estado, con margen de mejora.";
            case FAIR -> "Hay contraseñas que conviene cambiar.";
            case CRITICAL -> "Tu bóveda necesita atención ya.";
            case NONE -> "Aún no hay nada que revisar.";
        };
    }

    /** Pastilla con la nota («Excelente», «Mejorable»…): texto y color, nunca solo color. */
    private static final class GradeChip extends JComponent {
        private String text = "";
        private Color color = Color.GRAY;

        void set(VaultHealth.Grade g, Color c) {
            this.text = "Nota: " + g.label();
            this.color = c;
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(getFontMetrics(Draw.tracked(Fonts.bold(10), 0.06f)).stringWidth(text.toUpperCase()) + 20, 22);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Draw.quality(g);
            Draw.chip(g2, text, 0, 2, color, color, null);
            g2.dispose();
        }
    }

    /** Contador grande que lleva a la lista filtrada. */
    private final class Stat extends JButton {
        private final String label, icon, query;
        private int n;

        Stat(String label, String icon, String query) {
            this.label = label;
            this.icon = icon;
            this.query = query;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addActionListener(e -> {
                if (n > 0) applyQuery.accept(query);
            });
        }

        void set(int n) {
            this.n = n;
            setToolTipText(n > 0 ? "Ver las " + label.toLowerCase() + " en la lista" : "Nada que revisar");
            getAccessibleContext().setAccessibleName(label + ": " + n);
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(120, 96);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Draw.quality(g);
            Palette p = Draw.p();
            Color c = n == 0 ? p.ok() : label.startsWith("Antig") ? p.muted() : label.startsWith("Rep") ? p.warn() : p.error();
            Draw.box(g2, getModel().isRollover() && n > 0 ? p.high() : p.raised(), p.line1(), 0, 0, getWidth(), getHeight(), Draw.RADIUS);
            g2.setColor(c);
            g2.fillRect(0, 0, getWidth(), 3);
            Icons.colored(n == 0 ? "check" : icon, 16, () -> c).paintIcon(this, g2, getWidth() - 30, 14);
            g2.setFont(Fonts.bold(32));
            g2.setColor(n == 0 ? p.dim() : p.ink());
            g2.drawString(String.valueOf(n), 14, 56);
            g2.setFont(Draw.label().deriveFont(10f));
            g2.setColor(p.muted());
            g2.drawString(label.toUpperCase(), 14, getHeight() - 14);
            if (isFocusOwner()) Draw.focusRing(g2, 0, 0, getWidth(), getHeight(), 6);
            g2.dispose();
        }
    }

    /** Barras horizontales: nº de entradas por nivel de fortaleza. */
    private static final class Distribution extends JComponent {
        private int[] counts = new int[PasswordStrength.Level.values().length];
        private int total;

        void set(VaultHealth.Result r) {
            counts = r.strength().clone();
            total = 0;
            for (int c : counts) total += c;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(300, counts.length * 30);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Draw.quality(g);
            Palette p = Draw.p();
            PasswordStrength.Level[] levels = PasswordStrength.Level.values();
            int labelW = 110, numW = 34, barX = labelW, barW = Math.max(40, getWidth() - labelW - numW - 8);
            for (int i = levels.length - 1, row = 0; i >= 0; i--, row++) {
                int y = row * 30;
                g2.setFont(Fonts.regular(12));
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(p.dim());
                g2.drawString(levels[i].label(), 0, Draw.baseline(fm, y, 22));
                g2.setColor(p.line1());
                g2.fillRect(barX, y + 6, barW, 10);
                if (total > 0 && counts[i] > 0) {
                    g2.setColor(Draw.strengthColor(levels[i]));
                    g2.fillRect(barX, y + 6, Math.max(3, (int) Math.round(barW * counts[i] / (double) total)), 10);
                }
                g2.setFont(Fonts.bold(12));
                g2.setColor(p.ink());
                String n = String.valueOf(counts[i]);
                g2.drawString(n, getWidth() - g2.getFontMetrics().stringWidth(n), Draw.baseline(g2.getFontMetrics(), y, 22));
            }
            g2.dispose();
        }
    }

    /** Barra de cobertura de códigos 2FA. */
    private static final class Coverage extends JComponent {
        private int with, total;

        void set(int with, int total) {
            this.with = with;
            this.total = total;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(300, 56);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Draw.quality(g);
            Palette p = Draw.p();
            g2.setFont(Fonts.bold(22));
            g2.setColor(p.ink());
            g2.drawString(with + " de " + total, 0, 22);
            g2.setFont(Fonts.regular(12));
            g2.setColor(p.muted());
            g2.drawString("entradas con código de un solo uso", g2.getFontMetrics(Fonts.bold(22)).stringWidth(with + " de " + total) + 12, 22);
            g2.setColor(p.line1());
            g2.fillRect(0, 36, getWidth(), 10);
            if (total > 0 && with > 0) {
                g2.setColor(p.secondary());
                g2.fillRect(0, 36, Math.max(3, (int) Math.round(getWidth() * with / (double) total)), 10);
            }
            g2.dispose();
        }
    }

    /** Las entradas con peor estado, para ir directo a ellas. */
    private final class FixList extends JPanel {
        private final List<PasswordEntry> items = new ArrayList<>();
        private VaultHealth.Result r;
        private int hover = -1;

        FixList() {
        setOpaque(false);
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int i = e.getY() / 52;
                    if (i != hover) {
                        hover = i < items.size() ? i : -1;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = -1;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    int i = e.getY() / 52;
                    if (i >= 0 && i < items.size()) openEntry.accept(items.get(i));
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
            getAccessibleContext().setAccessibleName("Entradas a revisar");
        }

        void set(VaultHealth.Result r) {
            this.r = r;
            items.clear();
            List<PasswordEntry> all = new ArrayList<>();
            for (var f : r.report().weak()) addUnique(all, f.entry());
            for (var f : r.report().empty()) addUnique(all, f.entry());
            for (var f : r.report().reused()) addUnique(all, f.entry());
            for (var f : r.report().old()) addUnique(all, f.entry());
            all.sort(Comparator.comparingDouble((PasswordEntry e) -> r.of(e).health()).thenComparing(e -> e.getTitle() == null ? "" : e.getTitle().toLowerCase()));
            items.addAll(all.subList(0, Math.min(6, all.size())));
            revalidate();
            repaint();
        }

        private static void addUnique(List<PasswordEntry> list, PasswordEntry e) {
            if (!list.contains(e)) list.add(e);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(300, Math.max(1, items.size()) * 52);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Draw.quality(g);
            Palette p = Draw.p();
            if (items.isEmpty()) {
                g2.setFont(Fonts.regular(13));
                g2.setColor(p.ok());
                Icons.colored("check", 16, () -> Draw.p().ok()).paintIcon(this, g2, 0, 14);
                g2.drawString(r != null && r.total() == 0 ? "Sin entradas todavía." : "Nada urgente: todo en orden.", 26, 27);
                g2.dispose();
                return;
            }
            for (int i = 0; i < items.size(); i++) {
                PasswordEntry e = items.get(i);
                VaultHealth.EntryHealth h = r.of(e);
                int y = i * 52;
                if (i == hover) Draw.fillBox(g2, p.high(), -6, y, getWidth() + 12, 50, Draw.RADIUS);
                new Icons.Avatar(e.getTitle(), e.getUrl(), 32).paintIcon(this, g2, 0, y + 9);
                g2.setFont(Fonts.bold(13));
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(p.ink());
                int right = getWidth();
                int cx = right;
                List<String> tags = new ArrayList<>();
                List<Color> cols = new ArrayList<>();
                if (h.has(VaultHealth.Flag.EMPTY)) { tags.add("Vacía"); cols.add(p.error()); }
                if (h.has(VaultHealth.Flag.WEAK)) { tags.add("Débil"); cols.add(p.error()); }
                if (h.has(VaultHealth.Flag.REUSED)) { tags.add("Repetida"); cols.add(p.warn()); }
                if (h.has(VaultHealth.Flag.OLD)) { tags.add("Antigua"); cols.add(p.muted()); }
                for (int t = tags.size() - 1; t >= 0; t--) {
                    int cw = Draw.chipWidth(g2, tags.get(t));
                    cx -= cw;
                    Draw.chip(g2, tags.get(t), cx, y + 16, cols.get(t), cols.get(t), null);
                    cx -= 6;
                }
                g2.setFont(Fonts.bold(13));
                g2.drawString(Draw.ellipsize(e.getTitle() == null ? "" : e.getTitle(), fm, Math.max(40, cx - 52)), 44, y + 24);
                g2.setFont(Fonts.regular(11));
                g2.setColor(p.muted());
                g2.drawString(Draw.ellipsize(Sites.domain(e.getUrl()), g2.getFontMetrics(), Math.max(40, cx - 52)), 44, y + 40);
                g2.setColor(p.line1());
                g2.fillRect(0, y + 51, getWidth(), 1);
            }
            g2.dispose();
        }
    }
}
