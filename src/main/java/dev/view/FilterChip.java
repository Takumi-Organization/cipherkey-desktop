package dev.view;

import javax.swing.*;
import java.awt.*;

/** Filtro rápido conmutable: etiqueta en mayúsculas y contador; activo = relleno de acento. Accesible por teclado. */
public class FilterChip extends JToggleButton {
    private int count = -1;
    private final String label;

    public FilterChip(String label) {
        this.label = label;
        setFocusable(true);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        getAccessibleContext().setAccessibleName("Filtro " + label);
    }

    public void setCount(int count) {
        this.count = count;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(Draw.tracked(Fonts.bold(11), 0.06f));
        int w = fm.stringWidth(label.toUpperCase()) + 22 + (count >= 0 ? fm.stringWidth(String.valueOf(count)) + 10 : 0);
        return new Dimension(w, 28);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Draw.quality(g);
        Palette p = Draw.p();
        boolean on = isSelected();
        Color fg = on ? p.onAccent() : p.dim();
        Draw.box(g2, on ? p.accent() : (getModel().isRollover() ? p.high() : null), on ? null : p.line2(), 0, 0, getWidth(), getHeight(), Draw.RADIUS);
        g2.setFont(Draw.tracked(Fonts.bold(11), 0.06f));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(fg);
        int x = 11;
        g2.drawString(label.toUpperCase(), x, Draw.baseline(fm, 0, getHeight()));
        if (count >= 0) {
            x += fm.stringWidth(label.toUpperCase()) + 8;
            g2.setColor(on ? p.onAccent() : (count > 0 ? p.error() : p.faint()));
            g2.drawString(String.valueOf(count), x, Draw.baseline(fm, 0, getHeight()));
        }
        if (isFocusOwner()) Draw.focusRing(g2, 0, 0, getWidth(), getHeight(), 6);
        g2.dispose();
    }
}
