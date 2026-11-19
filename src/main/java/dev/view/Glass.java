package dev.view;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Cristal de Ryoku Glass para Swing. Java2D no tiene «backdrop-filter», así que se captura lo que queda DETRÁS de la
 * superficie, se desenfoca a mano (tres pasadas de desenfoque de caja) y se pinta: base opaca del color de fondo +
 * imagen desenfocada + velo + filete con brillo arriba. La base opaca es imprescindible: sin ella, lo nítido de debajo
 * se transparenta a través del velo.
 *
 * <p>Es una captura ÚNICA (no sigue al contenido): vale para superficies efímeras como los avisos. Úsalo solo en capas
 * que flotan, nunca en listas ni en texto largo.
 */
public final class Glass {
    private static final int BLUR_RADIUS = 9;

    private Glass() {}

    /** Captura {@code area} (en coordenadas de {@code source}) tal como está pintado ahora y la devuelve desenfocada. */
    public static BufferedImage frost(JComponent source, Rectangle area) {
        BufferedImage img = new BufferedImage(Math.max(1, area.width), Math.max(1, area.height), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.translate(-area.x, -area.y);
        g.setClip(area);
        source.paint(g);
        g.dispose();
        int[] px = ((java.awt.image.DataBufferInt) img.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < 3; i++) boxBlur(px, img.getWidth(), img.getHeight(), BLUR_RADIUS);
        return img;
    }

    /** Pinta la superficie de cristal en (0,0,w,h) con esquinas de diámetro {@code arc}. {@code frost} puede ser null (opaco). */
    public static void paint(Graphics2D g, Palette p, BufferedImage frost, int w, int h, int arc) {
        RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, w, h, arc, arc);
        Shape oldClip = g.getClip();
        g.clip(shape);
        g.setColor(p.paper());
        g.fillRect(0, 0, w, h);
        if (frost != null) g.drawImage(frost, 0, 0, w, h, null);
        g.setColor(Palette.alpha(p.raised(), frost != null ? (p.dark() ? 0.60 : 0.66) : 0.96));
        g.fillRect(0, 0, w, h);
        g.setClip(oldClip);
        // filete: más claro arriba (brillo de 1 px), tinta fina abajo
        Color top = p.dark() ? new Color(255, 255, 255, 0x24) : new Color(255, 255, 255, 0xE6);
        Color edge = p.inkAlpha(p.dark() ? 0.12 : 0.10);
        g.setPaint(new GradientPaint(0, 0, top, 0, h, edge));
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, arc, arc));
    }

    /** Desenfoque de caja separable con bordes replicados (un solo canal a la vez sobre ARGB). */
    private static void boxBlur(int[] px, int w, int h, int r) {
        int[] tmp = new int[px.length];
        pass(px, tmp, w, h, r, true);
        pass(tmp, px, w, h, r, false);
    }

    private static void pass(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int n = 2 * r + 1;
        int len = horizontal ? w : h, lines = horizontal ? h : w;
        for (int line = 0; line < lines; line++) {
            for (int i = 0; i < len; i++) {
                int a = 0, rr = 0, gg = 0, bb = 0;
                for (int k = -r; k <= r; k++) {
                    int j = Math.max(0, Math.min(len - 1, i + k));
                    int c = horizontal ? in[line * w + j] : in[j * w + line];
                    a += c >>> 24; rr += (c >> 16) & 0xFF; gg += (c >> 8) & 0xFF; bb += c & 0xFF;
                }
                int o = (a / n << 24) | (rr / n << 16) | (gg / n << 8) | (bb / n);
                if (horizontal) out[line * w + i] = o; else out[i * w + line] = o;
            }
        }
    }
}
