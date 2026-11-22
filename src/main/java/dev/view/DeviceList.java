package dev.view;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/** Lista de dispositivos como tarjetas planas: icono, nombre, detalle, pastillas de estado y dato a la derecha. */
final class DeviceList extends JList<DeviceList.Item> {
    record Item(String icon, String name, String meta, List<Chips.Spec> chips, String right) {}

    private static final int HEIGHT = 66;
    private final DefaultListModel<Item> model = new DefaultListModel<>();
    private String emptyText = "";

    DeviceList(String accessibleName) {
        setModel(model);
        setCellRenderer(new Renderer());
        setFixedCellHeight(HEIGHT);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        getAccessibleContext().setAccessibleName(accessibleName);
    }

    void setItems(List<Item> items) {
        model.clear();
        items.forEach(model::addElement);
        revalidate();
        repaint();
    }

    void setEmptyText(String text) {
        this.emptyText = text;
        repaint();
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(400, HEIGHT * Math.max(2, Math.min(4, model.size())));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (model.isEmpty() && !emptyText.isEmpty()) {
            Graphics2D g2 = Draw.quality(g);
            g2.setFont(Fonts.regular(12));
            g2.setColor(Draw.p().muted());
            g2.drawString(emptyText, 16, 36);
            g2.dispose();
        }
    }

    private static final class Renderer extends JComponent implements ListCellRenderer<Item> {
        private Item item;
        private boolean selected, focus;

        @Override
        public Component getListCellRendererComponent(JList<? extends Item> list, Item value, int index, boolean isSelected, boolean hasFocus) {
            item = value;
            selected = isSelected;
            focus = isSelected && list.isFocusOwner();
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(300, HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Draw.quality(g);
            Palette p = Draw.p();
            int w = getWidth(), h = getHeight();
            // seleccionado = inversión (fondo tinta, texto fondo)
            Color main = selected ? p.onFill() : p.ink(), sub = selected ? p.onFill() : p.muted();
            Draw.box(g2, selected ? p.fill() : p.raised(), selected ? p.fill() : p.lineHigh(), 0, 2, w, h - 4, Draw.CUT_SM);
            Draw.box(g2, selected ? p.fill() : p.paper(), selected ? p.onFill() : p.lineHigh(), 14, (h - 38) / 2, 38, 38, 0);
            Color iconColor = main;
            Icons.colored(item.icon(), 20, () -> iconColor).paintIcon(this, g2, 23, (h - 20) / 2);
            int x = 66;
            g2.setFont(Fonts.bold(14));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(main);
            int right = w - 14;
            if (item.right() != null && !item.right().isEmpty()) {
                g2.setFont(Fonts.regular(11));
                FontMetrics rf = g2.getFontMetrics();
                g2.setColor(sub);
                g2.drawString(item.right(), right - rf.stringWidth(item.right()), h / 2 + 15);
            }
            int cx = right;
            for (int i = item.chips().size() - 1; i >= 0; i--) {
                Chips.Spec s = item.chips().get(i);
                int cw = Draw.chipWidth(g2, s.text());
                cx -= cw;
                if (selected) Draw.chip(g2, s.text(), cx, h / 2 - 20, p.onFill(), p.onFill(), null);
                else Draw.chip(g2, s.text(), cx, h / 2 - 20, s.fg(), s.border(), s.fill());
                cx -= 6;
            }
            g2.setFont(Fonts.bold(14));
            g2.setColor(main);
            g2.drawString(Draw.ellipsize(item.name(), fm, Math.max(60, cx - x - 8)), x, h / 2 - 3);
            g2.setFont(Fonts.regular(11));
            g2.setColor(sub);
            g2.drawString(Draw.ellipsize(item.meta(), g2.getFontMetrics(), Math.max(60, cx - x - 8)), x, h / 2 + 15);
            if (focus) Draw.focusRing(g2, 2, 3, w - 4, h - 6, 0, p.onFill());
            g2.dispose();
        }
    }
}
