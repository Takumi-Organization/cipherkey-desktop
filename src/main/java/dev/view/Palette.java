package dev.view;

import java.awt.Color;
import java.util.List;

/**
 * Tokens de color de «Ryoku Glass»: neutros fríos, tinta, filetes finos y UN acento por tema. Dos modos: oscuro (por
 * defecto) y claro. Es un valor inmutable; {@link ThemeManager} guarda el activo.
 */
public record Palette(boolean dark, Color paper, Color raised, Color high, Color ink, Color dim, Color muted, Color faint,
                      Color accent, Color onAccent, Color ok, Color warn, Color error, Color secondary) {

    /** Acento predefinido: cada uno trae su variante para fondo oscuro y para fondo claro (contraste AA medido sobre el papel). */
    public record Accent(String key, String label, Color forDark, Color forLight) {
        public Color on(boolean dark) {
            return dark ? forDark : forLight;
        }
    }

    /**
     * Las CLAVES se conservan (se guardan en las preferencias): «ambar» es Oro y «azul» es Egeo. «naranja» se retiró; un
     * valor guardado que no exista cae al acento por defecto.
     */
    public static final List<Accent> ACCENTS = List.of(
            new Accent("bermellon", "Bermellón", new Color(0xFF5A4A), new Color(0xD12B1F)),
            new Accent("ambar", "Oro", new Color(0xF2B33D), new Color(0x8F5F00)),
            new Accent("lima", "Lima", new Color(0xB6E04A), new Color(0x4F7A00)),
            new Accent("fucsia", "Fucsia", new Color(0xFF6FB1), new Color(0xC4166F)),
            new Accent("azul", "Egeo", new Color(0x62B6E8), new Color(0x0B6B99)));

    public static final String DEFAULT_ACCENT = "ambar";

    /** Paleta de un modo con un acento concreto (ya adaptado al modo). */
    public static Palette of(boolean dark, Color accent) {
        Color onAccent = bestText(accent, dark ? new Color(0x0F1115) : Color.WHITE, dark ? Color.WHITE : new Color(0x0F1115));
        return dark
                ? new Palette(true, c(0x0F1115), c(0x171A20), c(0x232830), c(0xECEFF4), c(0xB4BBC7), c(0x8E97A6), c(0x5F6877),
                        accent, onAccent, c(0x6FD3A6), c(0xF0B25E), c(0xFF7A70), c(0x7FC4EC))
                : new Palette(false, c(0xF4F5F7), c(0xFFFFFF), c(0xE9ECF0), c(0x14171C), c(0x4A515C), c(0x5F6773), c(0x9AA2AE),
                        accent, onAccent, c(0x1B6B49), c(0x8A5A00), c(0xB3261E), c(0x0B6B99));
    }

    /** Paleta con un acento predefinido (por clave) o personalizado (hex #RRGGBB); si no se reconoce, el de por defecto. */
    public static Palette resolve(boolean dark, String accentKey, String customHex) {
        if ("custom".equals(accentKey)) {
            Color c = parseHex(customHex);
            if (c != null) return of(dark, ensureContrast(c, dark ? c(0x0F1115) : c(0xF4F5F7), 3.0));
        }
        for (Accent a : ACCENTS) {
            if (a.key().equals(accentKey)) return of(dark, a.on(dark));
        }
        for (Accent a : ACCENTS) {
            if (a.key().equals(DEFAULT_ACCENT)) return of(dark, a.on(dark));
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

    /** Tinta con una opacidad dada (0–1): los filetes son tinta al 10 / 16 / 30 %. */
    public Color inkAlpha(double a) {
        return alpha(ink, a);
    }

    public Color line1() { return inkAlpha(0.10); }
    public Color line2() { return inkAlpha(0.16); }
    public Color line3() { return inkAlpha(0.30); }

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
