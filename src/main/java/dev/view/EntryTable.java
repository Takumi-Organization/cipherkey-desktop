package dev.view;

import dev.model.PasswordEntry;
import dev.security.VaultHealth;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.function.Predicate;

/**
 * Lista de entradas: una fila rica por entrada (avatar, nombre, usuario y sitio, categoría, avisos de salud,
 * fuerza y favorita). Ordenable y filtrable; trabaja siempre con índices de modelo.
 */
public class EntryTable extends JTable {
    public enum Sort {
        NAME("Nombre"), RECENT("Recientes"), WEAKEST("Más débiles primero");

        private final String label;

        Sort(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final int ROW_HEIGHT = 66;

    private final PasswordTableModel model;
    private final TableRowSorter<PasswordTableModel> sorter;
    private VaultHealth.Result health;
    private int hoverRow = -1;

    public EntryTable(PasswordTableModel model) {
        super(model);
        this.model = model;
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setFillsViewportHeight(true);
        setShowGrid(false);
        setRowHeight(ROW_HEIGHT);
        setIntercellSpacing(new Dimension(0, 0));
        setTableHeader(null);
        setRowSelectionAllowed(true);
        getAccessibleContext().setAccessibleName("Lista de entradas");

        sorter = new TableRowSorter<>(model);
        setRowSorter(sorter);
        setSort(Sort.NAME);
        getColumnModel().getColumn(0).setCellRenderer(new RowRenderer());

        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                setHover(rowAtPoint(e.getPoint()));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setHover(-1);
            }
        };
        addMouseMotionListener(hover);
        addMouseListener(hover);
    }

    private void setHover(int row) {
        if (row != hoverRow) {
            hoverRow = row;
            repaint();
        }
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (getTableHeader() != null) setTableHeader(null);
    }

    /** Estado de salud (avisos y fuerza) con el que se pintan las filas. */
    public void setHealth(VaultHealth.Result health) {
        this.health = health;
        repaint();
    }

    public VaultHealth.EntryHealth healthOf(PasswordEntry e) {
        return health == null ? null : health.of(e);
    }

    public void setSort(Sort sort) {
        Comparator<PasswordEntry> byName = Comparator.comparing((PasswordEntry e) -> nz(e.getTitle()), String.CASE_INSENSITIVE_ORDER);
        Comparator<PasswordEntry> cmp = switch (sort) {
            case NAME -> byName;
            case RECENT -> Comparator.comparing(PasswordEntry::getModified).reversed().thenComparing(byName);
            case WEAKEST -> Comparator.comparingDouble((PasswordEntry e) -> {
                VaultHealth.EntryHealth h = healthOf(e);
                return h == null ? 1 : h.health();
            }).thenComparing(byName);
        };
        sorter.setComparator(0, (Comparator<Object>) (a, b) -> cmp.compare((PasswordEntry) a, (PasswordEntry) b));
        sorter.setSortKeys(java.util.List.of(new RowSorter.SortKey(0, SortOrder.ASCENDING)));
        sorter.sort();
    }

    /** Vuelve a ordenar (p. ej. tras recalcular la salud con el orden «más débiles»). */
    public void resort() {
        sorter.sort();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    public void setFilter(Predicate<PasswordEntry> predicate) {
        sorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends PasswordTableModel, ? extends Integer> e) {
                return predicate.test(e.getModel().entryAt(e.getIdentifier()));
            }
        });
    }

    public PasswordEntry selectedEntry() {
        int row = getSelectedRow();
        return row < 0 ? null : model.entryAt(convertRowIndexToModel(row));
    }

    public void selectById(int id) {
        for (int viewRow = 0; viewRow < getRowCount(); viewRow++) {
            if (model.entryAt(convertRowIndexToModel(viewRow)).getId() == id) {
                setRowSelectionInterval(viewRow, viewRow);
                scrollRectToVisible(getCellRect(viewRow, 0, true));
                return;
            }
        }
        clearSelection();
    }

    /** Fila rica pintada a mano: un solo componente, sin jerarquía de paneles. */
    private final class RowRenderer extends JComponent implements TableCellRenderer {
        private PasswordEntry entry;
        private boolean selected, focused, hover;
        private Icon avatar;

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            entry = (PasswordEntry) value;
            selected = isSelected;
            focused = isSelected && t.isFocusOwner();
            hover = row == hoverRow;
            avatar = new Icons.Avatar(entry.getTitle(), entry.getUrl(), 40);
            setSize(t.getColumnModel().getColumn(0).getWidth(), ROW_HEIGHT);
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Draw.quality(g);
            Palette p = Draw.p();
            int w = getWidth(), h = getHeight();
            VaultHealth.EntryHealth hl = healthOf(entry);

            g2.setColor(p.paper());
            g2.fillRect(0, 0, w, h);
            if (selected) {
                g2.setColor(p.high());
                g2.fillRect(0, 0, w, h);
                g2.setColor(p.accent());
                g2.fillRect(0, 0, 4, h);
            } else if (hover) {
                g2.setColor(p.accentTint(0.06));
                g2.fillRect(0, 0, w, h);
            }
            g2.setColor(p.line1());
            g2.fillRect(18, h - 1, w - 36, 1);

            int x = 20;
            avatar.paintIcon(this, g2, x, (h - 40) / 2);
            x += 40 + 14;

            // columna derecha (se calcula primero para saber cuánto sitio queda al texto)
            int right = w - 18;
            if (entry.isFavorite()) {
                Icon star = Icons.colored("star-filled", 14, () -> Draw.p().warn());
                star.paintIcon(this, g2, right - 14, (h - 14) / 2);
                right -= 14 + 14;
            }
            boolean wide = w >= 620, mid = w >= 500;
            if (wide && hl != null && !hl.has(VaultHealth.Flag.EMPTY)) {
                Draw.segments(g2, hl.level(), false, right - 52, (h - 5) / 2, 52, 5, 2);
                right -= 52 + 16;
            }
            // avisos de salud como iconos con color de estado (nunca solo color: icono distinto por tipo)
            if (hl != null && mid) {
                right = flagIcon(g2, hl.has(VaultHealth.Flag.EMPTY) || hl.has(VaultHealth.Flag.WEAK), "alert", p.error(), right, h);
                right = flagIcon(g2, hl.has(VaultHealth.Flag.REUSED), "repeat", p.warn(), right, h);
                right = flagIcon(g2, hl.has(VaultHealth.Flag.OLD), "clock", p.muted(), right, h);
            }
            if (mid && entry.getTotpSecret() != null && !entry.getTotpSecret().isBlank()) {
                g2.setFont(Draw.tracked(Fonts.bold(10), 0.06f));
                int cw = Draw.chipWidth(g2, "2FA");
                Draw.chip(g2, "2FA", right - cw, (h - 18) / 2, p.secondary(), p.line2(), null);
                right -= cw + 10;
            }
            String cat = entry.getCategory();
            if (wide && cat != null && !cat.isBlank()) {
                g2.setFont(Draw.tracked(Fonts.bold(10), 0.06f));
                String c = Draw.ellipsize(cat.toUpperCase(), g2.getFontMetrics(), 110);
                int cw = Draw.chipWidth(g2, c);
                Draw.chip(g2, c, right - cw, (h - 18) / 2, p.dim(), p.line2(), null);
                right -= cw + 12;
            }

            int textW = Math.max(40, right - x - 8);
            g2.setFont(Fonts.bold(14));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(p.ink());
            g2.drawString(Draw.ellipsize(nz(entry.getTitle()), fm, textW), x, h / 2 - 4);
            g2.setFont(Fonts.regular(12));
            fm = g2.getFontMetrics();
            g2.setColor(p.muted());
            String sub = subtitle(entry);
            g2.drawString(Draw.ellipsize(sub, fm, textW), x, h / 2 + 15);

            if (focused) Draw.focusRing(g2, 2, 2, w - 4, h - 4, 4);
            g2.dispose();
        }

        private int flagIcon(Graphics2D g2, boolean on, String icon, Color color, int right, int h) {
            if (!on) return right;
            Icons.colored(icon, 16, () -> color).paintIcon(this, g2, right - 16, (h - 16) / 2);
            return right - 16 - 8;
        }
    }

    static String subtitle(PasswordEntry e) {
        String user = nz(e.getUsername()), site = Sites.domain(e.getUrl());
        if (user.isEmpty()) return site.isEmpty() ? "—" : site;
        return site.isEmpty() ? user : user + "  •  " + site;
    }
}
