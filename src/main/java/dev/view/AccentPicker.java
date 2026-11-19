package dev.view;

import dev.persistence.AppPrefs;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Selector del acento: seis predefinidos y uno personalizado. Aplica y guarda al instante. */
public class AccentPicker extends JPanel {
    private static final int SW = 28, GAP = 8;
    private final Runnable onChange;
    private int hover = -1;

    public AccentPicker(Runnable onChange) {
        this.onChange = onChange;
        setOpaque(false);
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        getAccessibleContext().setAccessibleName("Color de acento");
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int i = indexAt(e.getX());
                if (i != hover) {
                    hover = i;
                    setToolTipText(i < 0 ? null : i < Palette.ACCENTS.size() ? Palette.ACCENTS.get(i).label() : "Personalizado");
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = -1;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int i = indexAt(e.getX());
                if (i >= 0) choose(i);
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                int n = Palette.ACCENTS.size() + 1, cur = currentIndex();
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_RIGHT) choose(Math.min(n - 1, cur + 1));
                else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_LEFT) choose(Math.max(0, cur - 1));
            }
        });
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    private int indexAt(int x) {
        int i = x / (SW + GAP);
        return i <= Palette.ACCENTS.size() && x - i * (SW + GAP) <= SW ? i : -1;
    }

    private int currentIndex() {
        String k = AppPrefs.accent();
        for (int i = 0; i < Palette.ACCENTS.size(); i++) if (Palette.ACCENTS.get(i).key().equals(k)) return i;
        return "custom".equals(k) ? Palette.ACCENTS.size() : 0;
    }

    private void choose(int i) {
        if (i < Palette.ACCENTS.size()) {
            AppPrefs.setAccent(Palette.ACCENTS.get(i).key());
        } else {
            Color start = Palette.parseHex(AppPrefs.customAccent());
            Color c = JColorChooser.showDialog(this, "Color de acento personalizado", start == null ? Draw.p().accent() : start);
            if (c == null) return;
            AppPrefs.setCustomAccent(Palette.toHex(c));
            AppPrefs.setAccent("custom");
        }
        ThemeManager.reapply();
        onChange.run();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        int n = Palette.ACCENTS.size() + 1;
        return new Dimension(n * SW + (n - 1) * GAP + 4, SW + 6);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Draw.quality(g);
        Palette p = Draw.p();
        int cur = currentIndex();
        for (int i = 0; i <= Palette.ACCENTS.size(); i++) {
            int x = 2 + i * (SW + GAP), y = 3;
            boolean custom = i == Palette.ACCENTS.size();
            Color fill = custom ? Palette.resolve(p.dark(), "custom", AppPrefs.customAccent()).accent() : Palette.ACCENTS.get(i).on(p.dark());
            if (custom && !"custom".equals(AppPrefs.accent())) {
                Draw.box(g2, null, p.line3(), x, y, SW, SW, 4);
                Draw.cross(g2, x + SW / 2, y + SW / 2, 5, p.dim());
            } else {
                Draw.fillBox(g2, fill, x, y, SW, SW, 4);
            }
            if (i == cur) {
                g2.setColor(p.ink());
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(x - 3, y - 3, SW + 5, SW + 5, 6, 6);
                Icons.colored("check", 14, () -> Palette.bestText(fill, new Color(0x211D1A), Color.WHITE)).paintIcon(this, g2, x + (SW - 14) / 2, y + (SW - 14) / 2);
            } else if (i == hover) {
                g2.setColor(p.line3());
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(x - 3, y - 3, SW + 5, SW + 5, 6, 6);
            }
        }
        if (isFocusOwner()) Draw.focusRing(g2, 0, 0, getWidth(), getHeight(), 6);
        g2.dispose();
    }
}
