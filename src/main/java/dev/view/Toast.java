package dev.view;

import javax.swing.*;
import java.awt.*;

/** Aviso breve y no modal, en la parte inferior de la ventana: superficie de cristal (lo de debajo desenfocado) con un icono de acento. */
public final class Toast {
    private static final String KEY = "ck.toast";
    private static final int DURATION_MS = 2400;

    private Toast() {}

    public static void show(Component anchor, String message) {
        JRootPane root = SwingUtilities.getRootPane(anchor);
        if (root == null) return;
        JLayeredPane layers = root.getLayeredPane();

        Object previous = layers.getClientProperty(KEY);
        if (previous instanceof Component old) {
            layers.remove(old);
        }

        float[] progress = {0f};
        java.awt.image.BufferedImage[] frost = {null};
        JPanel toast = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 11)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Draw.quality(g);
                Palette p = Draw.p();
                Glass.paint(g2, p, frost[0], getWidth(), getHeight(), Draw.RADIUS);
                g2.dispose();
            }

            @Override
            public void paint(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, progress[0]));
                super.paint(g2);
                g2.dispose();
            }
        };
        toast.setOpaque(false);
        toast.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 8));
        JLabel text = new JLabel(message, Icons.colored("check", 16, () -> Draw.p().accent()), SwingConstants.LEADING);
        text.setIconTextGap(10);
        text.setForeground(Draw.p().ink());
        toast.add(text);

        Dimension size = toast.getPreferredSize();
        int x = (layers.getWidth() - size.width) / 2;
        int rest = layers.getHeight() - size.height - 28;
        toast.setBounds(x, rest, size.width, size.height);
        // captura de lo que queda debajo ANTES de añadir el aviso (si no, se desenfocaría a sí mismo)
        frost[0] = Glass.frost(layers, new Rectangle(x, rest, size.width, size.height));
        layers.add(toast, JLayeredPane.POPUP_LAYER);
        layers.putClientProperty(KEY, toast);
        Motion.run(140, t -> {
            progress[0] = (float) t;
            toast.setLocation(x, rest + (int) Math.round((1 - t) * 10));
            toast.repaint();
        }, null);
        layers.repaint();

        Timer timer = new Timer(DURATION_MS, e -> {
            layers.remove(toast);
            layers.repaint();
        });
        timer.setRepeats(false);
        timer.start();
    }
}
