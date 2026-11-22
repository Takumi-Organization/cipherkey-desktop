package dev.view;

import java.awt.Color;

/**
 * Tokens de color de Takumi (fuente de verdad: {@code takumi-design/tokens.json}): monocromo estricto, sin acento.
 * Dos variantes, oscuro («papel negro», por defecto) y claro («papel»). Es un valor inmutable; {@link ThemeManager}
 * guarda la activa.
 *
 * <p>Correspondencia con los tokens: {@code paper}=fondo, {@code raised}=panel, {@code high}=elevado, {@code ink}=tinta,
 * {@code dim}=tinta suave, {@code muted}=tinta apagada, {@code faint}=tinta tenue (solo decorativa: no cumple 4,5:1),
 * {@code line}=línea, {@code lineHigh}=línea alta. La inversión (seleccionado, botón primario) pinta con
 * {@link #fill()} (tinta) y escribe con {@link #onFill()} (fondo).
 */
public record Palette(boolean dark, Color paper, Color raised, Color high, Color ink, Color dim, Color muted, Color faint,
                      Color line, Color lineHigh) {

    public static final Palette DARK = new Palette(true, c(0x000000), c(0x080808), c(0x111111), c(0xF2F2F2), c(0xC9C9C9),
            c(0x8C8C8C), c(0x4A4A4A), c(0x1C1C1C), c(0x3A3A3A));
    public static final Palette LIGHT = new Palette(false, c(0xF4F4F4), c(0xFFFFFF), c(0xEBEBEB), c(0x0A0A0A), c(0x333333),
            c(0x6B6B6B), c(0xB0B0B0), c(0xE0E0E0), c(0xC0C0C0));

    /** Escala de grises de los gráficos (de más a menos tinta). */
    public static final Color[] GRAYS = {c(0xF2F2F2), c(0xC9C9C9), c(0x8C8C8C), c(0x5E5E5E), c(0x3A3A3A), c(0x1C1C1C)};

    public static Palette of(boolean dark) {
        return dark ? DARK : LIGHT;
    }

    private static Color c(int rgb) {
        return new Color(rgb);
    }

    // ---- inversión ----

    /** Fondo de lo seleccionado / activo / botón primario: la tinta. */
    public Color fill() {
        return ink;
    }

    /** Texto sobre {@link #fill()}: el color del fondo. */
    public Color onFill() {
        return paper;
    }

    // ---- derivados ----

    public static Color alpha(Color c, double a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(Math.max(0, Math.min(1, a)) * 255));
    }

    /** Mezcla {@code a} sobre {@code base} (sin transparencia: para pintar fondos opacos). */
    public static Color mix(Color base, Color a, double t) {
        return new Color(
                (int) Math.round(base.getRed() + (a.getRed() - base.getRed()) * t),
                (int) Math.round(base.getGreen() + (a.getGreen() - base.getGreen()) * t),
                (int) Math.round(base.getBlue() + (a.getBlue() - base.getBlue()) * t));
    }

    public static String toHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    // ---- contraste (WCAG 2.x) ----

    public static double luminance(Color c) {
        double r = lin(c.getRed() / 255.0), g = lin(c.getGreen() / 255.0), b = lin(c.getBlue() / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double lin(double v) {
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    public static double contrast(Color a, Color b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** Saturación HSB (0 = gris puro). */
    public static float saturation(Color c) {
        return Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null)[1];
    }
}
