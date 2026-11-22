package dev.view;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Tokens de Takumi: contraste WCAG AA de la tinta y monocromo estricto (ningún color de la paleta tiene saturación). */
class PaletteTest {
    private static final Palette[] BOTH = {Palette.DARK, Palette.LIGHT};

    @Test
    void inkMeetsContrastOnEverySurface() {
        for (Palette p : BOTH) {
            for (Color surface : new Color[]{p.paper(), p.raised(), p.high()}) {
                assertTrue(Palette.contrast(p.ink(), surface) >= 7, "tinta sobre " + Palette.toHex(surface));
                assertTrue(Palette.contrast(p.dim(), surface) >= 4.5, "tinta suave sobre " + Palette.toHex(surface));
            }
        }
    }

    @Test
    void mutedTextMeetsAaOnPaperAndPanel() {
        // La tinta apagada es solo para texto secundario sobre fondo y panel (no sobre el elevado del modo claro).
        for (Palette p : BOTH) {
            for (Color surface : new Color[]{p.paper(), p.raised()}) {
                assertTrue(Palette.contrast(p.muted(), surface) >= 4.5, "apagada " + p.dark() + " sobre " + Palette.toHex(surface));
            }
        }
    }

    @Test
    void invertedPairsMeetAa() {
        for (Palette p : BOTH) {
            assertTrue(Palette.contrast(p.onFill(), p.fill()) >= 7, "texto sobre inversión " + p.dark());
            assertEquals(p.ink(), p.fill());
            assertEquals(p.paper(), p.onFill());
        }
    }

    @Test
    void paletteIsStrictlyMonochrome() {
        for (Palette p : BOTH) {
            for (Color c : new Color[]{p.paper(), p.raised(), p.high(), p.ink(), p.dim(), p.muted(), p.faint(), p.line(), p.lineHigh()}) {
                assertEquals(0f, Palette.saturation(c), 1e-6, "color con saturación: " + Palette.toHex(c));
            }
        }
        for (Color c : Palette.GRAYS) assertEquals(0f, Palette.saturation(c), 1e-6);
    }

    @Test
    void tokensMatchTakumiDesign() {
        assertEquals("#000000", Palette.toHex(Palette.DARK.paper()));
        assertEquals("#F2F2F2", Palette.toHex(Palette.DARK.ink()));
        assertEquals("#F4F4F4", Palette.toHex(Palette.LIGHT.paper()));
        assertEquals("#0A0A0A", Palette.toHex(Palette.LIGHT.ink()));
        assertEquals(Palette.DARK, Palette.of(true));
        assertEquals(Palette.LIGHT, Palette.of(false));
    }

    @Test
    void themeDefaultsInjectOnlyGrayColors() {
        for (Palette p : BOTH) {
            Map<String, String> m = ThemeManager.defaults(p);
            for (Map.Entry<String, String> e : m.entrySet()) {
                String v = e.getValue();
                if (!v.matches("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")) continue;
                Color c = new Color(Integer.parseInt(v.substring(1, 7), 16));
                assertEquals(0f, Palette.saturation(c), 1e-6, e.getKey() + " = " + v + " no es gris");
            }
        }
    }
}
