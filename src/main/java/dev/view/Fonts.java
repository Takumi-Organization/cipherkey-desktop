package dev.view;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;

/**
 * Tipografía de Ryoku Glass, embebida (OFL, ver resources/fonts/LICENSE-*.txt): Inter para toda la interfaz y
 * JetBrains Mono solo para datos (contraseñas, códigos de un solo uso, dígitos de verificación). Si no cargan, cae a la
 * fuente lógica del sistema.
 */
public final class Fonts {
    private static Font regular, bold, monoRegular, monoBold;
    private static boolean loaded;

    private Fonts() {}

    /** Registra las fuentes en el entorno gráfico. Idempotente. Devuelve la familia a usar en el Look & Feel. */
    public static synchronized String install() {
        if (!loaded) {
            loaded = true;
            regular = load("/fonts/Inter-Regular.ttf");
            bold = load("/fonts/Inter-Bold.ttf");
            monoRegular = load("/fonts/JetBrainsMono-Regular.ttf");
            monoBold = load("/fonts/JetBrainsMono-Bold.ttf");
        }
        return regular != null ? regular.getFamily() : Font.SANS_SERIF;
    }

    private static Font load(String resource) {
        try (InputStream in = Fonts.class.getResourceAsStream(resource)) {
            if (in == null) return null;
            Font f = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    public static Font regular(float size) {
        install();
        return (regular != null ? regular : new Font(Font.SANS_SERIF, Font.PLAIN, 12)).deriveFont(Font.PLAIN, size);
    }

    public static Font bold(float size) {
        install();
        return (bold != null ? bold : new Font(Font.SANS_SERIF, Font.BOLD, 12)).deriveFont(Font.PLAIN, size);
    }

    /** Monoespaciada para DATOS (contraseñas, códigos): JetBrains Mono. */
    public static Font mono(float size) {
        install();
        return (monoRegular != null ? monoRegular : new Font(Font.MONOSPACED, Font.PLAIN, 12)).deriveFont(Font.PLAIN, size);
    }

    public static Font monoBold(float size) {
        install();
        return (monoBold != null ? monoBold : new Font(Font.MONOSPACED, Font.BOLD, 12)).deriveFont(Font.PLAIN, size);
    }
}
