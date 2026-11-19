package dev.view;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WhenTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 12, 0);

    @Test
    void relativeInSpanishUnits() {
        assertEquals("hoy", When.relative(NOW.minusHours(5), NOW));
        assertEquals("ayer", When.relative(NOW.minusDays(1), NOW));
        assertEquals("hace 12 días", When.relative(NOW.minusDays(12), NOW));
        assertEquals("hace 1 mes", When.relative(NOW.minusDays(35), NOW));
        assertEquals("hace 6 meses", When.relative(NOW.minusDays(190), NOW));
        assertEquals("hace 1 año", When.relative(NOW.minusDays(400), NOW));
        assertEquals("hace 2 años", When.relative(NOW.minusDays(800), NOW));
    }
}
