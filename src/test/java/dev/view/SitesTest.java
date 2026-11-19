package dev.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SitesTest {
    @Test
    void domainStripsSchemeWwwAndPath() {
        assertEquals("ejemplo.test", Sites.domain("https://www.Ejemplo.test/ruta?x=1"));
        assertEquals("ejemplo.test", Sites.domain("ejemplo.test"));
        assertEquals("", Sites.domain(""));
        assertEquals("", Sites.domain(null));
        assertEquals("", Sites.domain("http://[roto"));
    }

    @Test
    void sameSiteSharesColorKeyAndFallsBackToTitle() {
        assertEquals(Sites.colorKey("A", "https://x.test/a"), Sites.colorKey("B", "http://www.x.test"));
        assertEquals("mi nota", Sites.colorKey("Mi Nota", ""));
    }

    @Test
    void initialIsUppercaseFirstSymbol() {
        assertEquals("N", Sites.initial("nimbus", ""));
        assertEquals("?", Sites.initial("", ""));
        assertEquals("E", Sites.initial("", "https://ejemplo.test"));
    }
}
