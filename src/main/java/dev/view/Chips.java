package dev.view;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/** Fila de pastillas (categoría, 2FA, avisos). El estado nunca va solo en color: cada pastilla lleva glifo (● ▲ ✕) y texto. */
public class Chips extends JComponent {
    public record Spec(String text, Color fg, Color border, Color fill) {
        /** Neutra: texto y borde de línea alta. */
        public static Spec plain(String text, Color fg) {
            return new Spec(text, fg, Draw.p().lineHigh(), null);
        }

        /** Correcto: «● texto», borde de tinta. */
        public static Spec ok(String text) {
            Palette p = Draw.p();
            return new Spec(Draw.G_OK + " " + text, p.ink(), p.dim(), null);
        }

        /** Aviso: «▲ texto», borde de tinta. */
        public static Spec warn(String text) {
            Palette p = Draw.p();
            return new Spec(Draw.G_WARN + " " + text, p.ink(), p.ink(), null);
        }

        /** Error: «✕ texto» por INVERSIÓN (fondo tinta, texto fondo), no por color. */
        public static Spec error(String text) {
            Palette p = Draw.p();
            return new Spec(Draw.G_ERR + " " + text, p.onFill(), p.fill(), p.fill());
        }

        /** Resaltada: inversión sin glifo (p. ej. «Este equipo»). */
        public static Spec inverted(String text) {
            Palette p = Draw.p();
            return new Spec(text, p.onFill(), p.fill(), p.fill());
        }
    }

    private List<Spec> specs = List.of();

    public Chips() {
        setOpaque(false);
    }

    public void set(List<Spec> specs) {
        this.specs = specs;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(10, specs.isEmpty() ? 0 : 20);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Draw.quality(g);
        int x = 0;
        for (Spec s : specs) {
            x += Draw.chip(g2, s.text(), x, 1, s.fg(), s.border(), s.fill()) + 6;
        }
        g2.dispose();
    }
}
