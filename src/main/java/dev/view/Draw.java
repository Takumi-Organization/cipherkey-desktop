package dev.view;

import dev.security.PasswordStrength;

import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;

/** Primitivas de pintado planas (Java2D) de Ryoku Glass: sin sombras ni degradados (el cristal vive en {@link Glass}). */
public final class Draw {
    /** Diámetro del arco de las esquinas (RoundRectangle2D): 20 = radio de 10, el «control» de Glass. */
    public static final int RADIUS = 20;

    private Draw() {}

    public static Palette p() {
        return ThemeManager.palette();
    }

    public static Graphics2D quality(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    /** Fuente con espaciado entre letras (para las micro-etiquetas en mayúsculas, tracking .08em). */
    public static Font tracked(Font f, float tracking) {
        return f.deriveFont(Map.of(TextAttribute.TRACKING, tracking));
    }

    public static Font label() {
        return tracked(Fonts.bold(11), 0.08f);
    }

    public static void fillBox(Graphics2D g, Color fill, int x, int y, int w, int h, int arc) {
        g.setColor(fill);
        g.fill(new RoundRectangle2D.Float(x, y, w, h, arc, arc));
    }

    /** Recuadro de 1 px (borde fino) con radio. */
    public static void box(Graphics2D g, Color fill, Color border, int x, int y, int w, int h, int arc) {
        if (fill != null) fillBox(g, fill, x, y, w, h, arc);
        if (border != null) {
            g.setColor(border);
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1, h - 1, arc, arc));
        }
    }

    /** Anillo de foco de 2 px separado 2 px del control (accesibilidad: foco siempre visible). */
    public static void focusRing(Graphics2D g, int x, int y, int w, int h, int arc) {
        g.setColor(p().accent());
        g.setStroke(new BasicStroke(2f));
        g.draw(new RoundRectangle2D.Float(x + 1, y + 1, w - 2, h - 2, arc, arc));
    }

    /** Texto centrado en un rectángulo; devuelve su anchura. */
    public static int centeredText(Graphics2D g, String text, int x, int y, int w, int h) {
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(text);
        g.drawString(text, x + (w - tw) / 2, y + (h - fm.getHeight()) / 2 + fm.getAscent());
        return tw;
    }

    public static int baseline(FontMetrics fm, int y, int h) {
        return y + (h - fm.getHeight()) / 2 + fm.getAscent();
    }

    /** Recorta el texto con «…» para que quepa en el ancho dado. */
    public static String ellipsize(String text, FontMetrics fm, int width) {
        if (text == null) return "";
        if (fm.stringWidth(text) <= width) return text;
        String e = "…";
        int end = text.length();
        while (end > 0 && fm.stringWidth(text.substring(0, end) + e) > width) end--;
        return end <= 0 ? "" : text.substring(0, end) + e;
    }

    /** Pastilla de etiqueta: texto en mayúsculas, borde fino o relleno plano. Devuelve su ancho. */
    public static int chip(Graphics2D g, String text, int x, int y, Color fg, Color border, Color fill) {
        Font old = g.getFont();
        g.setFont(tracked(Fonts.bold(10), 0.06f));
        FontMetrics fm = g.getFontMetrics();
        String t = text.toUpperCase();
        int w = fm.stringWidth(t) + 14, h = 18;
        box(g, fill, border, x, y, w, h, 12);
        g.setColor(fg);
        g.drawString(t, x + 7, baseline(fm, y, h));
        g.setFont(old);
        return w;
    }

    public static int chipWidth(Graphics2D g, String text) {
        FontMetrics fm = g.getFontMetrics(tracked(Fonts.bold(10), 0.06f));
        return fm.stringWidth(text.toUpperCase()) + 14;
    }

    /** Fuerza en 5 segmentos planos: los llenos usan el color del nivel. */
    public static void segments(Graphics2D g, PasswordStrength.Level level, boolean empty, int x, int y, int w, int h, int gap) {
        Palette p = p();
        int n = 5, filled = empty ? 0 : level.ordinal() + 1;
        int sw = (w - gap * (n - 1)) / n;
        Color c = strengthColor(level);
        for (int i = 0; i < n; i++) {
            g.setColor(i < filled ? c : p.line1());
            g.fillRect(x + i * (sw + gap), y, sw, h);
        }
    }

    public static Color strengthColor(PasswordStrength.Level level) {
        Palette p = p();
        return switch (level) {
            case VERY_WEAK, WEAK -> p.error();
            case FAIR -> p.warn();
            case STRONG, VERY_STRONG -> p.ok();
        };
    }

    /** Anillo plano: pista fina + arco de valor (0–1) que arranca arriba y avanza en sentido horario. */
    public static void ring(Graphics2D g, double value, int x, int y, int size, float thickness, Color track, Color fg) {
        float half = thickness / 2f;
        Arc2D.Float full = new Arc2D.Float(x + half, y + half, size - thickness, size - thickness, 0, 360, Arc2D.OPEN);
        g.setStroke(new BasicStroke(thickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.setColor(track);
        g.draw(full);
        if (value > 0) {
            g.setColor(fg);
            g.draw(new Arc2D.Float(x + half, y + half, size - thickness, size - thickness, 90, -360 * (float) Math.min(1, value), Arc2D.OPEN));
        }
    }

    /** Fondo de puntos de 16 px (motivo del estilo). */
    public static void dotGrid(Graphics2D g, int x, int y, int w, int h, Color c) {
        g.setColor(c);
        for (int yy = y + 8; yy < y + h; yy += 16) {
            for (int xx = x + 8; xx < x + w; xx += 16) {
                g.fillRect(xx, yy, 2, 2);
            }
        }
    }

    /** Disco-sol plano (único motivo recurrente). */
    public static void sun(Graphics2D g, int cx, int cy, int r, Color c) {
        g.setColor(c);
        g.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
    }

    /** Marca de mira «+». */
    public static void cross(Graphics2D g, int cx, int cy, int arm, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(cx - arm, cy, cx + arm, cy);
        g.drawLine(cx, cy - arm, cx, cy + arm);
    }
}
