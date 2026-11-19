package dev.view;

import com.formdev.flatlaf.FlatClientProperties;
import dev.model.PasswordEntry;
import dev.security.VaultHealth;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Barra lateral: marca, vistas con contadores, avisos de salud, categorías y accesos (sincronizar, ajustes, bloquear). */
public class Sidebar extends JPanel {
    public static final String KEY_ALL = "all", KEY_HEALTH = "health";

    /**
     * Una vista de la lista de entradas; {@code key} identifica la vista para conservar la selección.
     * {@code tone} colorea el contador (avisos); {@code dot} pinta un cuadrado de color en vez de icono (categorías).
     */
    public record View(String key, String label, Icon icon, int count, Predicate<PasswordEntry> filter, Color dot, String badge,
                       boolean alert) {
        public View(String key, String label, Icon icon, int count, Predicate<PasswordEntry> filter) {
            this(key, label, icon, count, filter, null, null, false);
        }
    }

    private final DefaultListModel<Object> listModel = new DefaultListModel<>();
    private final JList<Object> list = new JList<>(listModel);
    private final Consumer<View> onSelect;
    private boolean updating;

    public Sidebar(Consumer<View> onSelect, Runnable onSettings, Runnable onLock) {
        super(new BorderLayout());
        this.onSelect = onSelect;
        setPreferredSize(new Dimension(244, 0));
        setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        list.setOpaque(false);
        list.setCellRenderer(new Renderer());
        list.setFixedCellHeight(-1);
        list.getAccessibleContext().setAccessibleName("Vistas de la bóveda");
        list.setSelectionModel(new DefaultListSelectionModel() {
            @Override
            public void setSelectionInterval(int from, int to) {
                if (from >= 0 && from < listModel.size() && listModel.get(from) instanceof String) {
                    // cabecera: salta a la siguiente vista con la flecha en la misma dirección
                    int cur = getMinSelectionIndex();
                    int step = from >= cur ? 1 : -1;
                    int i = from + step;
                    while (i >= 0 && i < listModel.size() && listModel.get(i) instanceof String) i += step;
                    if (i >= 0 && i < listModel.size()) super.setSelectionInterval(i, i);
                    return;
                }
                super.setSelectionInterval(from, to);
            }
        });
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || updating) return;
            Object v = list.getSelectedValue();
            if (v instanceof View view) {
                onSelect.accept(view);
            } else if (v == null) {
                list.setSelectedIndex(0);
            }
        });

        JPanel bottom = new JPanel(new GridLayout(0, 1, 0, 2));
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 10, 0, 10));
        bottom.add(footer("Ajustes", "sliders", "Tema, acento, bloqueo y clave maestra", onSettings));
        bottom.add(footer("Bloquear bóveda", "lock", "Bloquear (Ctrl+L)", onLock));

        add(new Brand(), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        add(scroll, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    private static JButton footer(String text, String icon, String tip, Runnable action) {
        JButton b = new JButton(text, Icons.get(icon, 16));
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        b.putClientProperty(FlatClientProperties.STYLE, "margin: 6,12,6,12");
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setIconTextGap(12);
        b.setToolTipText(tip);
        b.addActionListener(e -> action.run());
        return b;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setBackground(UIManager.getColor("App.sidebar.background"));
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        g.setColor(Draw.p().line1());
        g.fillRect(getWidth() - 1, 0, 1, getHeight());
    }

    /** Reconstruye las vistas con los recuentos actuales, conservando la vista seleccionada si sigue existiendo. */
    public void refresh(List<PasswordEntry> entries, VaultHealth.Result health) {
        String keep = list.getSelectedValue() instanceof View v ? v.key() : KEY_ALL;
        updating = true;
        listModel.clear();

        long favorites = entries.stream().filter(PasswordEntry::isFavorite).count();
        long totp = entries.stream().filter(e -> e.getTotpSecret() != null && !e.getTotpSecret().isBlank()).count();
        listModel.addElement("Bóveda");
        listModel.addElement(new View(KEY_ALL, "Todas las entradas", Icons.get("list"), entries.size(), e -> true));
        listModel.addElement(new View("favorites", "Favoritas", Icons.get("star"), (int) favorites, PasswordEntry::isFavorite));
        listModel.addElement(new View("2fa", "Con código 2FA", Icons.get("timer"), (int) totp,
                e -> e.getTotpSecret() != null && !e.getTotpSecret().isBlank()));

        listModel.addElement("Atención");
        listModel.addElement(new View(KEY_HEALTH, "Salud de la bóveda", Icons.get("shield-check"), -1, e -> true, null,
                health.total() == 0 ? null : String.valueOf(health.score()), false));
        listModel.addElement(flagView("weak", "Contraseñas débiles", "alert", health.weak(), health, VaultHealth.Flag.WEAK));
        listModel.addElement(flagView("reused", "Repetidas", "repeat", health.reused(), health, VaultHealth.Flag.REUSED));
        listModel.addElement(flagView("old", "Antiguas", "clock", health.old(), health, VaultHealth.Flag.OLD));

        Map<String, Integer> categories = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (PasswordEntry e : entries) {
            String c = e.getCategory();
            if (c != null && !c.isBlank()) categories.merge(c, 1, Integer::sum);
        }
        if (!categories.isEmpty()) {
            listModel.addElement("Categorías");
            categories.forEach((name, count) -> listModel.addElement(new View("cat:" + name.toLowerCase(), name, null, count,
                    e -> name.equalsIgnoreCase(e.getCategory()), Icons.Avatar.colorFor(name.toLowerCase()), null, false)));
        }

        int index = 1;
        for (int i = 0; i < listModel.size(); i++) {
            if (listModel.get(i) instanceof View v && v.key().equals(keep)) index = i;
        }
        list.setSelectedIndex(index);
        updating = false;
        onSelect.accept((View) listModel.get(index));
    }

    private static View flagView(String key, String label, String icon, int count, VaultHealth.Result health, VaultHealth.Flag flag) {
        return new View(key, label, Icons.get(icon), count, e -> {
            VaultHealth.EntryHealth h = health.of(e);
            return h != null && h.has(flag);
        }, null, null, count > 0);
    }

    /** Vuelve a la vista "Todas las entradas". */
    public void selectAll() {
        select(KEY_ALL);
    }

    public void select(String key) {
        for (int i = 0; i < listModel.size(); i++) {
            if (listModel.get(i) instanceof View v && v.key().equals(key)) {
                list.setSelectedIndex(i);
                return;
            }
        }
    }

    public List<String> categories() {
        return java.util.Collections.list(listModel.elements()).stream()
                .filter(o -> o instanceof View v && v.key().startsWith("cat:"))
                .map(o -> ((View) o).label()).toList();
    }

    /** Marca de la aplicación: bloque de acento con el candado y el nombre. */
    private static final class Brand extends JComponent {
        Brand() {
            setPreferredSize(new Dimension(0, 76));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Draw.quality(g);
            Palette p = Draw.p();
            Draw.fillBox(g2, p.accent(), 18, 22, 34, 34, Draw.RADIUS);
            Icons.onAccent("lock", 18).paintIcon(this, g2, 26, 30);
            g2.setColor(p.ink());
            g2.setFont(Fonts.bold(17));
            g2.drawString("CipherKey", 64, 38);
            g2.setColor(p.muted());
            g2.setFont(Draw.label().deriveFont(10f));
            g2.drawString("BÓVEDA LOCAL", 64, 53);
            g2.dispose();
        }
    }

    private static final class Renderer extends JComponent implements ListCellRenderer<Object> {
        private Object value;
        private boolean selected, focused;

        @Override
        public Component getListCellRendererComponent(JList<?> l, Object v, int index, boolean isSelected, boolean focus) {
            value = v;
            selected = isSelected && v instanceof View;
            focused = selected && l.isFocusOwner();
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(200, value instanceof String ? 40 : 38);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Draw.quality(g);
            Palette p = Draw.p();
            int w = getWidth(), h = getHeight();
            if (value instanceof String header) {
                g2.setFont(Draw.label());
                g2.setColor(p.muted());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(header.toUpperCase(), 20, h - 8);
                g2.dispose();
                return;
            }
            View v = (View) value;
            int x0 = 10, bw = w - 20;
            if (selected) {
                Draw.fillBox(g2, p.high(), x0, 1, bw, h - 2, Draw.RADIUS);
                g2.setColor(p.accent());
                g2.fillRect(x0, 5, 3, h - 10);
                if (focused) Draw.focusRing(g2, x0 - 1, 0, bw + 2, h, 6);
            }
            int x = x0 + 14;
            if (v.dot() != null) {
                Draw.fillBox(g2, v.dot(), x + 2, (h - 10) / 2, 10, 10, 4);
            } else if (v.icon() != null) {
                v.icon().paintIcon(this, g2, x, (h - v.icon().getIconHeight()) / 2);
            }
            g2.setFont(selected ? Fonts.bold(13) : Fonts.regular(13));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(selected ? p.ink() : p.dim());

            int right = x0 + bw - 12;
            String count = v.badge() != null ? v.badge() : v.count() >= 0 ? String.valueOf(v.count()) : "";
            if (!count.isEmpty()) {
                if (v.badge() != null) {
                    g2.setFont(Fonts.bold(11));
                    int cw = g2.getFontMetrics().stringWidth(count) + 12;
                    Draw.box(g2, selected ? p.accent() : null, selected ? null : p.line2(), right - cw, (h - 20) / 2, cw, 20, 12);
                    g2.setColor(selected ? p.onAccent() : p.ink());
                    Draw.centeredText(g2, count, right - cw, (h - 20) / 2, cw, 20);
                    right -= cw + 6;
                } else {
                    g2.setFont(Fonts.regular(12));
                    FontMetrics cf = g2.getFontMetrics();
                    int cw = cf.stringWidth(count);
                    g2.setColor(v.alert() ? p.error() : p.muted());
                    if (v.alert()) g2.setFont(Fonts.bold(12));
                    g2.drawString(count, right - cw, Draw.baseline(cf, 0, h));
                    right -= cw + 6;
                }
                g2.setFont(selected ? Fonts.bold(13) : Fonts.regular(13));
                g2.setColor(selected ? p.ink() : p.dim());
            }
            int tx = x + 28;
            g2.drawString(Draw.ellipsize(v.label(), fm, right - tx - 4), tx, Draw.baseline(fm, 0, h));
            g2.dispose();
        }
    }
}
