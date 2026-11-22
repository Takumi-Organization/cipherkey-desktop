package dev.view;

import javax.swing.*;
import java.awt.*;

/** Anillo plano con valor (0–1), texto central y subtexto. Sirve para la puntuación de salud y la cuenta atrás del 2FA. */
public class Ring extends JComponent {
    private double value;
    private String center = "", sub = "";
    private Color color;
    private final float thickness;
    private final float centerSize, subSize;

    public Ring(int size, float thickness, float centerSize, float subSize) {
        this.thickness = thickness;
        this.centerSize = centerSize;
        this.subSize = subSize;
        setPreferredSize(new Dimension(size, size));
        setMinimumSize(new Dimension(size, size));
        setOpaque(false);
    }

    public void setValue(double value) {
        this.value = Math.max(0, Math.min(1, value));
        repaint();
    }

    public double value() {
        return value;
    }

    public void setCenter(String center, String sub) {
        this.center = center == null ? "" : center;
        this.sub = sub == null ? "" : sub;
        repaint();
    }

    /** Color del arco; {@code null} usa la tinta. */
    public void setColor(Color color) {
        this.color = color;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Draw.quality(g);
        Palette p = Draw.p();
        int size = Math.min(getWidth(), getHeight());
        int x = (getWidth() - size) / 2, y = (getHeight() - size) / 2;
        Draw.ring(g2, value, x, y, size, thickness, p.lineHigh(), color != null ? color : p.ink());
        g2.setColor(p.ink());
        g2.setFont(Fonts.thin(centerSize));
        FontMetrics fm = g2.getFontMetrics();
        int cy = sub.isEmpty() ? y + (size - fm.getHeight()) / 2 + fm.getAscent() : y + size / 2 - 2;
        g2.drawString(center, x + (size - fm.stringWidth(center)) / 2, cy);
        if (!sub.isEmpty()) {
            g2.setFont(Draw.label().deriveFont(subSize));
            g2.setColor(p.muted());
            FontMetrics sf = g2.getFontMetrics();
            g2.drawString(sub, x + (size - sf.stringWidth(sub)) / 2, y + size / 2 + 4 + sf.getAscent());
        }
        g2.dispose();
    }
}
