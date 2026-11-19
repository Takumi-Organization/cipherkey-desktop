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
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        brand.setOpaque(false);
        brand.add(new JLabel(new ImageIcon(Icons.appIcon().getScaledInstance(60, 60, Image.SCALE_SMOOTH))));
        JLabel name = new JLabel("CipherKey");
        name.setFont(Fonts.bold(30));
        brand.add(name);
        body.centered(brand, 0);
        body.centered(Ui.muted("Versión " + AppInfo.version()), 6);
        body.centered(new JLabel("Gestor de contraseñas local y cifrado"), 16);
        body.centered(Ui.muted("Argon2id • AES-256-GCM • sin conexión a red"), 10);
        body.centered(Ui.muted("Licencia MIT • © 2026"), 4);
        JButton ok = new JButton("Cerrar");
        ok.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(ok);
        body.centered(ok, 18);

        setContentPane(body);
        pack();
        setLocationRelativeTo(parent);
    }

}
