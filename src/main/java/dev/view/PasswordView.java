package dev.view;

import javax.swing.*;
import java.awt.*;

/** Muestra una contraseña en monoespaciada: oculta con puntos o visible con letras, números y símbolos en tonos distintos. */
public class PasswordView extends JPanel {
    private String text = "";
    private boolean revealed;
    private final float size;
    private final boolean wrap;

    public PasswordView(float size, boolean wrap) {
        this.size = size;
        this.wrap = wrap;
        setOpaque(false);
    }

    public void setPassword(String text, boolean revealed) {
        this.text = text == null ? "" : text;
        this.revealed = revealed;
        getAccessibleContext().setAccessibleName(revealed ? "Contraseña visible" : "Contraseña oculta");
        revalidate();
        repaint();
    }

    private int charWidth() {
        return getFontMetrics(Fonts.monoBold(size)).charWidth('M');
    }

    private int perLine(int width) {
        return Math.max(8, (width - 4) / charWidth());
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(Fonts.monoBold(size));
        int w = getWidth() > 0 ? getWidth() : 360;
        int lines = wrap ? Math.max(1, (int) Math.ceil(text.length() / (double) perLine(w))) : 1;
        return new Dimension(wrap ? 10 : 200, lines * (fm.getHeight() + 2));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Draw.quality(g);
        Palette p = Draw.p();
        g2.setFont(Fonts.monoBold(size));
        FontMetrics fm = g2.getFontMetrics();
        int cw = fm.charWidth('M');
        if (text.isEmpty()) {
            g2.setColor(p.muted());
            g2.drawString("—", 0, fm.getAscent());
            g2.dispose();
            return;
        }
        if (!revealed) {
            g2.setColor(p.dim());
            int n = Math.min(text.length(), Math.max(8, (getWidth() - 4) / cw));
            StringBuilder dots = new StringBuilder();
            for (int i = 0; i < n; i++) dots.append('•');
            g2.drawString(dots.toString(), 0, fm.getAscent());
            g2.dispose();
            return;
        }
        int per = wrap ? perLine(getWidth()) : Integer.MAX_VALUE;
        int x = 0, y = fm.getAscent();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (i > 0 && i % per == 0) {
                x = 0;
                y += fm.getHeight() + 2;
            }
            if (!wrap && x + cw > getWidth()) {
                g2.setColor(p.muted());
                g2.drawString("…", x, y);
                break;
            }
            // tres tonos de gris: dígitos en tinta, letras en tinta suave, símbolos apagados
            g2.setColor(Character.isDigit(c) ? p.ink() : Character.isLetter(c) ? p.dim() : p.muted());
            g2.drawString(String.valueOf(c), x, y);
            x += cw;
        }
        g2.dispose();
    }
}
