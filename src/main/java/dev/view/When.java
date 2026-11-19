package dev.view;

import java.time.Duration;
import java.time.LocalDateTime;

/** Fechas relativas en español para la interfaz («hoy», «hace 3 días», «hace 2 años»). */
public final class When {
    private When() {}

    public static String relative(LocalDateTime then, LocalDateTime now) {
        long days = Duration.between(then, now).toDays();
        if (days < 0) return "en el futuro";
        if (days == 0) return "hoy";
        if (days == 1) return "ayer";
        if (days < 30) return "hace " + days + " días";
        long months = days / 30;
        if (months < 12) return "hace " + months + (months == 1 ? " mes" : " meses");
        long years = days / 365;
        return "hace " + years + (years == 1 ? " año" : " años");
    }
}
