package dev.view;

import dev.AppInfo;

import javax.swing.*;
import java.awt.*;

public class AboutDialog extends JDialog {
    public AboutDialog(Frame parent) {
        super(parent, "Acerca de CipherKey", true);
        Ui.configureDialog(this);

        VBox body = new VBox();
        body.setBorder(BorderFactory.createEmptyBorder(24, 40, 22, 40));
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 0));
        brand.setOpaque(false);
        brand.add(new JComponent() {
            { setPreferredSize(new Dimension(64, 64)); }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Draw.quality(g);
                Art.mark(g2, 0, 0, 64);
                g2.dispose();
            }
        });
        JLabel name = new JLabel("CIPHERKEY");
        name.setFont(Draw.tracked(Fonts.thin(30), 0.18f));
        brand.add(name);
        body.centered(brand, 0);
        body.centered(Ui.muted("Versión " + AppInfo.version()), 8);
        body.centered(new JLabel("Gestor de contraseñas local y cifrado"), 16);
        body.centered(Ui.muted("Argon2id · AES-256-GCM · sin conexión a red"), 10);
        body.centered(Ui.muted("Licencia MIT · © 2026 AdriLabTech"), 4);
        body.centered(Ui.muted("Identidad visual Takumi · tipografías Outfit y JetBrains Mono (SIL OFL 1.1)"), 4);
        JButton ok = Ui.secondary("Cerrar");
        ok.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(ok);
        body.centered(ok, 18);

        setContentPane(body);
        pack();
        setLocationRelativeTo(parent);
    }

}
