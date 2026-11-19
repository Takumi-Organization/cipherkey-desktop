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

    public static Icon accent(String name, int size) {
        return colored(name, size, () -> UIManager.getColor("Component.accentColor"));
    }

    /** Icono del color del texto sobre el acento (botón principal). */
    public static Icon onAccent(String name, int size) {
        return colored(name, size, () -> UIManager.getColor("App.onAccent"));
    }

    public static Icon ink(String name, int size) {
        return colored(name, size, () -> UIManager.getColor("Label.foreground"));
    }

    public static Icon colored(String name, int size, Supplier<Color> color) {
        // El mapper se evalúa al pintar, así que el icono cambia solo al alternar el tema.
        return new FlatSVGIcon("icons/" + name + ".svg", size, size, Icons.class.getClassLoader())
                .setColorFilter(new FlatSVGIcon.ColorFilter(c -> color.get()));
    }

    /** Imagen de la marca (escudo) para iconos de ventana. */
    public static Image appIcon() {
        return new ImageIcon(Icons.class.getResource("/img/icon.png")).getImage();
    }

    public static ImageIcon logo(int width) {
        ImageIcon raw = new ImageIcon(Icons.class.getResource("/img/logo.png"));
        int height = raw.getIconHeight() * width / raw.getIconWidth();
        return new ImageIcon(raw.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH));
    }

    /** Bloque plano de color con la inicial (estable por dominio; sin favicons ni peticiones de red). */
    public static final class Avatar implements Icon {
        /** Tonos tierra y agua, todos claros: la inicial va siempre en tinta oscura (contraste > 7:1). */
        private static final Color[] PALETTE = {
                new Color(0xD9A441), new Color(0x8FBDD1), new Color(0x7FD0AE), new Color(0xE08A6B),
                new Color(0xCBB89A), new Color(0xD98BA3), new Color(0xA3B565), new Color(0x9AA7B8)};
        private static final Color INK = new Color(0x211D1A);

        private final String letter;
        private final Color color;
        private final int size;

        public Avatar(String title, int size) {
            this(title, null, size);
        }

        public Avatar(String title, String url, int size) {
            this.letter = Sites.initial(title, url);
            this.color = colorFor(Sites.colorKey(title, url));
            this.size = size;
        }

        public static Color colorFor(String key) {
            return PALETTE[Math.floorMod(key.hashCode(), PALETTE.length)];
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = Draw.quality(g);
            Draw.fillBox(g2, color, x, y, size, size, Draw.RADIUS);
            g2.setColor(INK);
            g2.setFont(Fonts.bold(size * 0.46f));
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
