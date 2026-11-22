package dev.view;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;

/**
 * Tipografía de Takumi, embebida (OFL, ver resources/fonts/LICENSE-*.txt): Outfit para la interfaz (200 en títulos,
 * 300 en el cuerpo, 500 en énfasis y etiquetas de sección) y JetBrains Mono para datos y etiquetas (contraseñas,
 * códigos, micro-etiquetas). Java2D no maneja ejes variables, así que Outfit viaja como tres instancias estáticas
 * generadas de la fuente variable oficial. Si no cargan, cae a la fuente lógica del sistema.
 */
public final class Fonts {
    private static Font thin, light, medium, monoRegular, monoMedium;
    private static boolean loaded;

    private Fonts() {}

    /** Registra las fuentes en el entorno gráfico. Idempotente. Devuelve la familia a usar en el Look & Feel. */
    public static synchronized String install() {
        if (!loaded) {
            loaded = true;
            thin = load("/fonts/Outfit-ExtraLight.ttf");
            light = load("/fonts/Outfit-Light.ttf");
            medium = load("/fonts/Outfit-Medium.ttf");
            monoRegular = load("/fonts/JetBrainsMono-Regular.ttf");
            monoMedium = load("/fonts/JetBrainsMono-Medium.ttf");
        }
        return light != null ? light.getFamily() : Font.SANS_SERIF;
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

    private static Font pick(Font f, String fallback, int style, float size) {
        install();
        return (f != null ? f : new Font(fallback, style, 12)).deriveFont(Font.PLAIN, size);
    }

    /** Outfit 200: títulos y cifras grandes. */
    public static Font thin(float size) {
        return pick(thin, Font.SANS_SERIF, Font.PLAIN, size);
    }

    /** Outfit 300: cuerpo de texto. */
    public static Font regular(float size) {
        return pick(light, Font.SANS_SERIF, Font.PLAIN, size);
    }

    /** Outfit 500: énfasis (nombres, secciones). */
    public static Font bold(float size) {
        return pick(medium, Font.SANS_SERIF, Font.BOLD, size);
    }

    /** JetBrains Mono 400: DATOS (contraseñas, códigos) y etiquetas. */
    public static Font mono(float size) {
        return pick(monoRegular, Font.MONOSPACED, Font.PLAIN, size);
    }

    /** JetBrains Mono 500: datos destacados. */
    public static Font monoBold(float size) {
        return pick(monoMedium, Font.MONOSPACED, Font.BOLD, size);
    }
}
