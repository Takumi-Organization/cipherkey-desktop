package dev.view;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/** Fila de pastillas (categoría, 2FA, avisos). El estado nunca va solo en color: cada pastilla lleva texto. */
public class Chips extends JComponent {
    public record Spec(String text, Color fg, Color border, Color fill) {}

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
