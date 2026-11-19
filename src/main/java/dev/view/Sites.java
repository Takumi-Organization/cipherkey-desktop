package dev.view;

import java.net.URI;
import java.util.Locale;

/** Utilidades de texto sobre el sitio de una entrada. Todo local: nunca se consulta nada fuera del equipo. */
public final class Sites {
    private Sites() {}

    /** Dominio legible de una URL («https://www.ejemplo.test/a» → «ejemplo.test»), o cadena vacía. */
    public static String domain(String url) {
        if (url == null || url.isBlank()) return "";
        String u = url.trim();
        if (!u.contains("://")) u = "https://" + u;
        try {
            String host = URI.create(u).getHost();
            if (host == null) return "";
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /**
     * Clave estable para elegir el color del avatar: el dominio si hay, y si no el título. Dos entradas del mismo sitio
     * comparten color.
     */
    public static String colorKey(String title, String url) {
        String d = domain(url);
        if (!d.isEmpty()) return d;
        return title == null ? "" : title.trim().toLowerCase(Locale.ROOT);
    }

    /** Primera letra o número visible para el avatar, en mayúscula. */
    public static String initial(String title, String url) {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) t = domain(url);
        if (t.isEmpty()) return "?";
        return t.substring(0, t.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
    }
}
