package dev.view;

import java.awt.Color;
import java.util.List;

/**
 * Tokens de color del estilo «Ryoku Kami»: superficies planas, tinta, líneas finas y UN acento por tema.
 * Dos modos: Grafito (oscuro, por defecto) y Washi (claro). Es un valor inmutable; {@link ThemeManager} guarda el activo.
 */
public record Palette(boolean dark, Color paper, Color raised, Color high, Color ink, Color dim, Color muted, Color faint,
                      Color accent, Color onAccent, Color ok, Color warn, Color error, Color secondary) {

    /** Acento predefinido: cada uno trae su variante para fondo oscuro y para fondo claro (contraste AA medido sobre el papel). */
    public record Accent(String key, String label, Color forDark, Color forLight) {
        public Color on(boolean dark) {
            return dark ? forDark : forLight;
        }
    }

    public static final List<Accent> ACCENTS = List.of(
            new Accent("bermellon", "Bermellón", new Color(0xFF5A47), new Color(0xC42A21)),
            new Accent("naranja", "Naranja", new Color(0xFF7A29), new Color(0xB8420A)),
            new Accent("lima", "Lima", new Color(0xB6E83C), new Color(0x4A7300)),
            new Accent("fucsia", "Fucsia", new Color(0xFF5FB5), new Color(0xB80F73)),
            new Accent("azul", "Azul eléctrico", new Color(0x5C98FF), new Color(0x1B47D6)),
            new Accent("ambar", "Ámbar", new Color(0xFFB52E), new Color(0x8A5A00)));

    public static final String DEFAULT_ACCENT = "bermellon";

    /** Paleta de un modo con un acento concreto (ya adaptado al modo). */
    public static Palette of(boolean dark, Color accent) {
        Color onAccent = bestText(accent, dark ? new Color(0x211D1A) : Color.WHITE, dark ? Color.WHITE : new Color(0x211D1A));
        return dark
                ? new Palette(true, c(0x211D1A), c(0x2A2521), c(0x352F2A), c(0xECE4D8), c(0xC9C0B3), c(0xA69D90), c(0x8F867A),
                        accent, onAccent, c(0x7FD0AE), c(0xF0B25E), c(0xF27A70), c(0x8FBDD1))
                : new Palette(false, c(0xF3ECE0), c(0xFAF5EC), c(0xE9E0D1), c(0x26211C), c(0x4A423A), c(0x675D52), c(0x8A8074),
                        accent, onAccent, c(0x1F6F4D), c(0x8A5A00), c(0xB3261E), c(0x2B6A86));
    }

    /** Paleta con un acento predefinido (por clave) o personalizado (hex #RRGGBB); si no se reconoce, el de por defecto. */
    public static Palette resolve(boolean dark, String accentKey, String customHex) {
        if ("custom".equals(accentKey)) {
            Color c = parseHex(customHex);
            if (c != null) return of(dark, ensureContrast(c, dark ? c(0x211D1A) : c(0xF3ECE0), 3.0));
        }
        for (Accent a : ACCENTS) {
            if (a.key().equals(accentKey)) return of(dark, a.on(dark));
        }
        return of(dark, ACCENTS.get(0).on(dark));
    }

    public static Color parseHex(String hex) {
        if (hex == null) return null;
        String h = hex.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (!h.matches("[0-9a-fA-F]{6}")) return null;
        return new Color(Integer.parseInt(h, 16));
    }

    public static String toHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static Color c(int rgb) {
        return new Color(rgb);
    }

    // ---- derivados ----

    /** Tinta con una opacidad dada (0–1): las líneas finas son tinta al 16 / 30 / 48 %. */
    public Color inkAlpha(double a) {
        return alpha(ink, a);
    }

    public Color line1() { return inkAlpha(0.16); }
    public Color line2() { return inkAlpha(0.30); }
    public Color line3() { return inkAlpha(0.48); }

    /** Velo del acento (selección, chips). */
    public Color accentTint(double a) {
        return alpha(accent, a);
    }

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

    /** De dos textos posibles, el que más contrasta con el fondo. */
    public static Color bestText(Color background, Color first, Color second) {
        return contrast(background, first) >= contrast(background, second) ? first : second;
    }

    /** Acerca un color al blanco o al negro (según el papel) hasta alcanzar el contraste mínimo con el papel. */
    public static Color ensureContrast(Color c, Color paper, double min) {
        Color target = luminance(paper) < 0.5 ? Color.WHITE : Color.BLACK;
        Color out = c;
        for (int i = 0; i < 20 && contrast(out, paper) < min; i++) {
            out = mix(out, target, 0.1);
        }
        return out;
    }
}
