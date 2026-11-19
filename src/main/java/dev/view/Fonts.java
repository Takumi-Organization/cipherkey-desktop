package dev.view;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;

/** Komiki Mono (MIT, ver resources/fonts/LICENSE-KomikiMono.txt) embebida; si no carga, cae a una monoespaciada del sistema. */
public final class Fonts {
    public static final String FAMILY = "Komiki Mono";
    private static Font regular, bold;
    private static boolean loaded;

    private Fonts() {}

    /** Registra las fuentes en el entorno gráfico. Idempotente. Devuelve la familia a usar en el Look & Feel. */
    public static synchronized String install() {
        if (!loaded) {
            loaded = true;
            regular = load("/fonts/KomikiMono-Regular.otf");
            bold = load("/fonts/KomikiMono-Bold.otf");
        }
        return regular != null ? FAMILY : Font.MONOSPACED;
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
        return (regular != null ? regular : new Font(Font.MONOSPACED, Font.PLAIN, 12)).deriveFont(Font.PLAIN, size);
    }

    public static Font bold(float size) {
        install();
        return (bold != null ? bold : new Font(Font.MONOSPACED, Font.BOLD, 12)).deriveFont(Font.PLAIN, size);
    }
}
