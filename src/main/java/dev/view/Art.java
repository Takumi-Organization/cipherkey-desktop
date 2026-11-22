package dev.view;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;

/**
 * Ilustración de línea fina, monocroma: marcos de trazo en tinta con la esquina superior derecha cortada, marcas «+».
 * Objetos de software, no figuras.
 */
public final class Art {
    public enum Kind { VAULT, SELECT, LOCK, SYNC }

    private Art() {}

    /** Ventana plana con marco cortado (sin sombras) y contenido de trazo según el tipo. */
    public static void window(Graphics2D g, Kind kind, int x, int y, int w, int h) {
        Palette p = Draw.p();
        g.setColor(p.paper());
        g.fill(Draw.cutShape(x, y, w, h, Draw.CUT_LG));
        g.setColor(p.ink());
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.draw(Draw.cutShape(x + 0.5f, y + 0.5f, w - 1, h - 1, Draw.CUT_LG));
        g.drawLine(x, y + 24, x + w - 1, y + 24);
        for (int i = 0; i < 3; i++) g.drawRect(x + 10 + i * 14, y + 8, 8, 8);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        int bx = x + 16, by = y + 40, bw = w - 32, bh = h - 56;
        switch (kind) {
            case VAULT -> {
                // filas de lista con avatar cuadrado y líneas de texto
                int rows = Math.max(2, bh / 34);
                for (int i = 0; i < rows; i++) {
                    int ry = by + i * 34;
                    g.drawRect(bx, ry, 22, 22);
                    g.drawLine(bx + 34, ry + 6, bx + bw - 40 - (i % 3) * 18, ry + 6);
                    g.drawLine(bx + 34, ry + 18, bx + bw / 2 - (i % 2) * 14, ry + 18);
                }
            }
            case SELECT -> {
                g.drawRect(bx, by, 22, 22);
                g.drawLine(bx + 34, by + 6, bx + bw - 10, by + 6);
                g.drawLine(bx + 34, by + 18, bx + bw / 2, by + 18);
                for (int i = 0; i < 3; i++) g.drawRect(bx, by + 38 + i * 28, bw, 20);
            }
            case LOCK -> lock(g, x + w / 2, y + 24 + (h - 24) / 2, Math.min(w, h - 24) / 3);
            case SYNC -> {
                g.drawRect(bx, by, 28, 28);
                g.drawRect(bx + bw - 28, by + bh - 28, 28, 28);
                g.drawLine(bx + 28, by + 14, bx + bw - 14, by + 14);
                g.drawLine(bx + bw - 14, by + 14, bx + bw - 14, by + bh - 28);
            }
        }
    }

    private static java.awt.image.BufferedImage mark;

    /** Marca de Takumi (la misma del icono de la aplicación) en una baldosa negra de {@code size} px con la esquina cortada. */
    public static void mark(Graphics2D g, int x, int y, int size) {
        Palette p = Draw.p();
        if (mark == null) {
            try {
                mark = javax.imageio.ImageIO.read(Art.class.getResource("/img/icon.png"));
            } catch (Exception | Error e) {
                mark = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            }
        }
        Shape old = g.getClip();
        g.clip(Draw.cutShape(x, y, size, size, size > 30 ? Draw.CUT_SM : 8));
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(mark, x, y, size, size, null);
        g.setClip(old);
        Draw.box(g, null, p.dark() ? p.lineHigh() : p.ink(), x, y, size, size, size > 30 ? Draw.CUT_SM : 8);
    }

    /** Candado de trazo: cuerpo, arco y ojo de cerradura. */
    private static void lock(Graphics2D g, int cx, int cy, int u) {
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        g.drawRect(cx - u, cy - u / 3, u * 2, (int) (u * 1.5));
        Path2D shackle = new Path2D.Float();
        shackle.append(new Arc2D.Float(cx - u * 0.65f, cy - u * 1.55f, u * 1.3f, u * 1.8f, 0, 180, Arc2D.OPEN), false);
        g.draw(shackle);
        g.fillRect(cx - 4, cy + u / 4, 8, 8);
        g.drawLine(cx, cy + u / 4 + 8, cx, cy + u / 4 + 20);
    }

    /** Ilustración completa con pie de texto en un rectángulo. */
    public static JComponent panel(Kind kind, String caption) {
        return new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Draw.quality(g);
                Palette p = Draw.p();
                int w = getWidth(), h = getHeight();
                int ww = Math.min(240, w - 80), wh = Math.min(170, h - 80);
                int x = (w - ww) / 2, y = (h - wh) / 2 - 12;
                window(g2, kind, x, y, ww, wh);
                Draw.cross(g2, x - 22, y - 10, 6, p.ink());
                Draw.cross(g2, x + ww + 28, y + wh + 22, 6, p.ink());
                g2.setFont(Draw.label());
                g2.setColor(p.muted());
                g2.drawString(caption.toUpperCase(), x, y + wh + 40);
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(340, 300);
            }
        };
    }
}
