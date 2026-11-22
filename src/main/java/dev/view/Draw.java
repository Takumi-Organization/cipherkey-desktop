package dev.view;

import dev.security.PasswordStrength;

import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.util.Map;

/**
 * Primitivas de pintado de Takumi (Java2D): radio 0, esquina superior derecha cortada en diagonal, bordes de 1 px,
 * sin sombras, degradados ni desenfoque. El corte es de 10 / 14 / 22 px según el tamaño del componente.
 */
public final class Draw {
    /** Corte de la esquina superior derecha: controles, tarjetas y paneles grandes. */
    public static final int CUT_SM = 10, CUT_MD = 14, CUT_LG = 22;

    /** Glifos de estado (el estado nunca va solo en color): correcto, aviso, error. */
    public static final String G_OK = "●", G_WARN = "▲", G_ERR = "✕";

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

    /** Fuente con espaciado entre letras (etiquetas en mayúsculas). */
    public static Font tracked(Font f, float tracking) {
        return f.deriveFont(Map.of(TextAttribute.TRACKING, tracking));
    }

    /** Etiqueta de Takumi: JetBrains Mono, mayúsculas, espaciada. */
    public static Font label() {
        return tracked(Fonts.mono(11), 0.14f);
    }

    /** Fuente de las pastillas. */
    public static Font chipFont() {
        return tracked(Fonts.mono(10), 0.10f);
    }

    // ---- formas ----

    /** Rectángulo con la esquina superior derecha cortada {@code cut} px (0 = rectángulo). */
    public static Shape cutShape(float x, float y, float w, float h, float cut) {
        Path2D.Float s = new Path2D.Float();
        s.moveTo(x, y);
        if (cut > 0) {
            s.lineTo(x + w - cut, y);
            s.lineTo(x + w, y + cut);
        } else {
            s.lineTo(x + w, y);
        }
        s.lineTo(x + w, y + h);
        s.lineTo(x, y + h);
        s.closePath();
        return s;
    }

    public static void fillBox(Graphics2D g, Color fill, int x, int y, int w, int h, int cut) {
        g.setColor(fill);
        g.fill(cutShape(x, y, w, h, cut));
    }

    /** Recuadro de 1 px (borde fino) con la esquina cortada. */
    public static void box(Graphics2D g, Color fill, Color border, int x, int y, int w, int h, int cut) {
        if (fill != null) fillBox(g, fill, x, y, w, h, cut);
        if (border != null) {
            g.setColor(border);
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            g.draw(cutShape(x + 0.5f, y + 0.5f, w - 1, h - 1, cut));
        }
    }

    /** Foco = borde de tinta de 2 px (accesibilidad: el foco siempre se ve). */
    public static void focusRing(Graphics2D g, int x, int y, int w, int h, int cut) {
        focusRing(g, x, y, w, h, cut, p().ink());
    }

    public static void focusRing(Graphics2D g, int x, int y, int w, int h, int cut, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.draw(cutShape(x + 1, y + 1, w - 2, h - 2, cut));
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

    /** Pastilla de etiqueta: texto en mayúsculas mono, borde fino o relleno plano (inversión). Devuelve su ancho. */
    public static int chip(Graphics2D g, String text, int x, int y, Color fg, Color border, Color fill) {
        Font old = g.getFont();
        g.setFont(chipFont());
        FontMetrics fm = g.getFontMetrics();
        String t = text.toUpperCase();
        int w = fm.stringWidth(t) + 14, h = 18;
        box(g, fill, border, x, y, w, h, 0);
        g.setColor(fg);
        g.drawString(t, x + 7, baseline(fm, y, h));
        g.setFont(old);
        return w;
    }

    public static int chipWidth(Graphics2D g, String text) {
        return g.getFontMetrics(chipFont()).stringWidth(text.toUpperCase()) + 14;
    }

    // ---- fuerza y gráficos en grises ----

    /** Fuerza en 5 segmentos planos: cuántos se llenan indica el nivel (el color solo refuerza, en grises). */
    public static void segments(Graphics2D g, PasswordStrength.Level level, boolean empty, int x, int y, int w, int h, int gap) {
        segments(g, level, empty, x, y, w, h, gap, strengthColor(level), p().lineHigh());
    }

    /** Como {@link #segments} con colores dados (para filas invertidas). */
    public static void segments(Graphics2D g, PasswordStrength.Level level, boolean empty, int x, int y, int w, int h, int gap,
                                Color on, Color off) {
        int n = 5, filled = empty ? 0 : level.ordinal() + 1;
        int sw = (w - gap * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            g.setColor(i < filled ? on : off);
            g.fillRect(x + i * (sw + gap), y, sw, h);
        }
    }

    /** Gris del nivel de fortaleza: más tinta cuanto más fuerte (el texto del nivel siempre acompaña). */
    public static Color strengthColor(PasswordStrength.Level level) {
        Palette p = p();
        return switch (level) {
            case VERY_WEAK, WEAK -> p.muted();
            case FAIR -> p.dim();
            case STRONG, VERY_STRONG -> p.ink();
        };
    }

    /** Glifo y texto del nivel de fortaleza: ✕ débil, ▲ media, ● fuerte. */
    public static String strengthGlyph(PasswordStrength.Level level) {
        return switch (level) {
            case VERY_WEAK, WEAK -> G_ERR;
            case FAIR -> G_WARN;
            case STRONG, VERY_STRONG -> G_OK;
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

    /** Marca de mira «+». */
    public static void cross(Graphics2D g, int cx, int cy, int arm, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(cx - arm, cy, cx + arm, cy);
        g.drawLine(cx, cy - arm, cx, cy + arm);
    }
}
