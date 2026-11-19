package dev.view;

import javax.swing.*;
import java.awt.*;

/** Código de seguridad de la vinculación por proximidad: seis cifras grandes para comparar con la otra pantalla. */
final class SasDialog extends JDialog {
    private boolean match;

    SasDialog(Frame parent, String peer, String sas, boolean incoming) {
        super(parent, "Código de seguridad", true);
        Ui.configureDialog(this);

        JLabel what = new JLabel("<html>" + (incoming ? "«" + Ui.escapeHtml(peer) + "» quiere vincularse con este equipo."
                : "Vinculando con «" + Ui.escapeHtml(peer) + "».") + "</html>");
        what.setFont(Fonts.bold(15));
        JLabel help = new JLabel("<html>Comprueba que <b>el otro dispositivo muestra exactamente este código</b> antes de aceptar. "
                + "Si no coincide, alguien podría estar interponiéndose: pulsa «No coincide».</html>");
        help.putClientProperty("FlatLaf.style", "foreground: $App.muted");

        JButton yes = Ui.primary("Coincide", Icons.onAccent("check", 16), e -> {
            match = true;
            dispose();
        });
        JButton no = new JButton("No coincide");
        no.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(no); // por seguridad, Intro rechaza

        VBox body = new VBox();
        body.setBorder(BorderFactory.createEmptyBorder(24, 30, 22, 30));
        body.row(Ui.section(0, "Vinculación por proximidad"), 0);
        body.row(what, 10);
        body.row(new Code(sas), 18);
        body.row(help, 16);
        body.row(Ui.buttonBar(no, yes), 22);
        setContentPane(body);
        setPreferredSize(new Dimension(520, getPreferredSize().height));
        pack();
        setLocationRelativeTo(parent);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                no.requestFocusInWindow();
            }
        });
    }

    /** Muestra el diálogo (modal) y devuelve si el usuario confirma que los códigos coinciden. */
    boolean ask() {
        setVisible(true);
        return match;
    }

    private static final class Code extends JPanel {
        private final String digits;

        Code(String sas) {
            this.digits = sas;
        setOpaque(false);
            getAccessibleContext().setAccessibleName("Código de seguridad: " + String.join(" ", sas.split("")));
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(440, 92);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Draw.quality(g);
            Palette p = Draw.p();
            int bw = 54, bh = 76, gap = 8, mid = 28;
            int total = 6 * bw + 4 * gap + mid;
            int x0 = (getWidth() - total) / 2, y = (getHeight() - bh) / 2;
            g2.setFont(Fonts.bold(44));
            for (int i = 0; i < digits.length() && i < 6; i++) {
                int x = x0 + i * (bw + gap) + (i >= 3 ? mid - gap : 0);
                Draw.box(g2, p.raised(), p.accent(), x, y, bw, bh, Draw.RADIUS);
                g2.setColor(p.ink());
                Draw.centeredText(g2, String.valueOf(digits.charAt(i)), x, y, bw, bh);
            }
            g2.setColor(p.muted());
            g2.fillRect(x0 + 3 * (bw + gap) - gap + (mid - 10) / 2 + 0, y + bh / 2 - 1, 10, 3);
            g2.dispose();
        }
    }
}
