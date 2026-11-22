package dev.view;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;

/** Iconos SVG (carpeta resources/icons) recoloreados según el tema activo. */
public final class Icons {
    private static final int DEFAULT_SIZE = 16;

    private Icons() {}

    /** Icono gris neutro (sigue al tema). */
    public static Icon get(String name) {
        return get(name, DEFAULT_SIZE);
    }

    public static Icon get(String name, int size) {
        return colored(name, size, () -> UIManager.getColor("App.icon.color"));
    }

    /** Icono del color del texto sobre un fondo de tinta (botón primario, selección): el color del fondo. */
    public static Icon onFill(String name, int size) {
        return colored(name, size, () -> UIManager.getColor("App.onFill"));
    }

    public static Icon ink(String name, int size) {
        return colored(name, size, () -> UIManager.getColor("Label.foreground"));
    }

    /** Color impuesto temporalmente a TODOS los iconos (filas invertidas); solo se usa en el hilo de pintado. */
    private static Color forced;

    /** Ejecuta {@code paint} haciendo que cualquier icono se pinte de {@code color} (p. ej. sobre una fila invertida). */
    public static void withColor(Color color, Runnable paint) {
        Color old = forced;
        forced = color;
        try {
            paint.run();
        } finally {
            forced = old;
        }
    }

    public static Icon colored(String name, int size, Supplier<Color> color) {
        // El mapper se evalúa al pintar, así que el icono cambia solo al alternar el tema.
        return new FlatSVGIcon("icons/" + name + ".svg", size, size, Icons.class.getClassLoader())
                .setColorFilter(new FlatSVGIcon.ColorFilter(c -> forced != null ? forced : color.get()));
    }

    /** Icono de la marca Takumi (sobre negro) para las ventanas. */
    public static Image appIcon() {
        return new ImageIcon(Icons.class.getResource("/img/icon.png")).getImage();
    }

    /** Bloque plano gris con la inicial (estable por dominio; sin favicons ni peticiones de red). */
    public static final class Avatar implements Icon {
        /** Oscuro: baldosas de grises claros con la inicial en negro (contraste > 6:1). */
        private static final Color[] ON_DARK = {
                new Color(0xF2F2F2), new Color(0xC9C9C9), new Color(0x8C8C8C), new Color(0xDEDEDE)};
        /** Papel: baldosas de grises oscuros con la inicial en blanco (contraste > 5:1). */
        private static final Color[] ON_LIGHT = {
                new Color(0x0A0A0A), new Color(0x333333), new Color(0x5E5E5E), new Color(0x1C1C1C)};

        private final String letter;
        private final String key;
        private final int size;

        public Avatar(String title, int size) {
            this(title, null, size);
        }

        public Avatar(String title, String url, int size) {
            this.letter = Sites.initial(title, url);
            this.key = Sites.colorKey(title, url);
            this.size = size;
        }

        public static Color colorFor(String key) {
            Color[] palette = Draw.p().dark() ? ON_DARK : ON_LIGHT;
            return palette[Math.floorMod(key.hashCode(), palette.length)];
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = Draw.quality(g);
            Draw.fillBox(g2, colorFor(key), x, y, size, size, size >= 48 ? Draw.CUT_MD : size >= 32 ? 8 : 6);
            g2.setColor(Draw.p().dark() ? Color.BLACK : Color.WHITE);
            g2.setFont(Fonts.mono(size * 0.46f));
            Draw.centeredText(g2, letter, x, y, size, size);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }
}
