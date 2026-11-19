package dev.view;

import dev.security.PasswordStrength;

import javax.swing.*;
import java.awt.*;

/** Fuerza estimada de una contraseña: cinco segmentos planos + nivel y bits. */
public class StrengthMeter extends JPanel {
    private PasswordStrength.Level level = PasswordStrength.Level.VERY_WEAK;
    private double bits;
    private boolean empty = true;

    public StrengthMeter() {
        setOpaque(false);
        setFocusable(false);
    }

    public void update(char[] password) {
        update(new String(password));
    }

    public void update(String password) {
        if (password == null || password.isEmpty()) {
            empty = true;
        } else {
            PasswordStrength.Result r = PasswordStrength.evaluate(password);
            empty = false;
            level = r.level();
            bits = r.bits();
        }
        getAccessibleContext().setAccessibleName(empty ? "Sin contraseña" : "Fuerza: " + level.label());
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(260, 22);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Draw.quality(g);
        Palette p = Draw.p();
        int labelW = 150;
        Draw.segments(g2, level, empty, 0, (getHeight() - 6) / 2, getWidth() - labelW - 10, 6, 3);
        g2.setFont(Fonts.bold(11));
        FontMetrics fm = g2.getFontMetrics();
        String text = empty ? "" : level.label() + " • ~" + Math.round(bits) + " bits";
        g2.setColor(empty ? p.faint() : Draw.strengthColor(level));
        g2.drawString(Draw.ellipsize(text, fm, labelW), getWidth() - labelW, Draw.baseline(fm, 0, getHeight()));
        g2.dispose();
    }
}
