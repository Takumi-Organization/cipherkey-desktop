package dev.view;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/** Accesibilidad del tema: contraste WCAG AA de texto y acento en Grafito y Washi, con cada acento predefinido. */
class PaletteTest {
    @Test
    void inkAndSecondaryTextMeetAaOnBothSurfaces() {
        for (boolean dark : new boolean[]{true, false}) {
            Palette p = Palette.resolve(dark, Palette.DEFAULT_ACCENT, null);
            for (Color surface : new Color[]{p.paper(), p.raised()}) {
                assertTrue(Palette.contrast(p.ink(), surface) >= 7, "tinta " + dark);
                assertTrue(Palette.contrast(p.dim(), surface) >= 4.5, "dim " + dark);
                assertTrue(Palette.contrast(p.muted(), surface) >= 4.5, "muted " + dark);
                assertTrue(Palette.contrast(p.error(), surface) >= 4.5, "error " + dark);
                assertTrue(Palette.contrast(p.ok(), surface) >= 4.5, "ok " + dark);
                assertTrue(Palette.contrast(p.warn(), surface) >= 4.5, "warn " + dark);
            }
        }
    }

    @Test
    void everyPresetAccentWorksAsTextAndAsFill() {
        for (boolean dark : new boolean[]{true, false}) {
            for (Palette.Accent a : Palette.ACCENTS) {
                Palette p = Palette.resolve(dark, a.key(), null);
                assertEquals(a.on(dark), p.accent());
                assertTrue(Palette.contrast(p.accent(), p.paper()) >= 4.5, a.key() + " como texto, oscuro=" + dark);
                assertTrue(Palette.contrast(p.accent(), p.raised()) >= 4.5, a.key() + " sobre panel, oscuro=" + dark);
                assertTrue(Palette.contrast(p.onAccent(), p.accent()) >= 4.5, a.key() + " texto sobre acento, oscuro=" + dark);
            }
        }
    }

    @Test
    void customAccentIsPulledToReadableContrast() {
        Palette dark = Palette.resolve(true, "custom", "#222222");
        assertTrue(Palette.contrast(dark.accent(), dark.paper()) >= 3.0);
        Palette light = Palette.resolve(false, "custom", "#F0F0F0");
        assertTrue(Palette.contrast(light.accent(), light.paper()) >= 3.0);
        assertTrue(Palette.contrast(dark.onAccent(), dark.accent()) >= 4.5);
    }

    @Test
    void unknownOrInvalidAccentFallsBackToDefault() {
        assertEquals(Palette.resolve(true, "bermellon", null).accent(), Palette.resolve(true, "nope", null).accent());
        assertEquals(Palette.resolve(true, "bermellon", null).accent(), Palette.resolve(true, "custom", "zzz").accent());
        assertNull(Palette.parseHex("12345"));
        assertEquals(new Color(0x12AB34), Palette.parseHex("#12ab34"));
        assertEquals("#12AB34", Palette.toHex(new Color(0x12AB34)));
    }
}
