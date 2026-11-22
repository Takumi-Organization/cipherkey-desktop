package dev.view;

import com.formdev.flatlaf.FlatClientProperties;
import dev.persistence.AppPrefs;
import dev.persistence.Vault;
import dev.persistence.VaultException;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/** Pantalla de acceso: elegir (o crear) una bóveda y escribir la clave maestra. */
public class LoginDialog extends JDialog {
    private final JComboBox<String> pathBox = new JComboBox<>();
    private final JPasswordField passwordField = Ui.passwordField(20);
    private final Ui.ErrorLabel errorLabel = new Ui.ErrorLabel();
    private boolean succeeded;
    private Path selectedFile;

    /**
     * @param preselected bóveda a mostrar de entrada (p. ej. la que falló); si es null se usa la más reciente
     * @param error       mensaje inicial a mostrar bajo la contraseña, o null
     */
    public LoginDialog(Frame parent, Path preselected, String error) {
        super(parent, "CipherKey", true);
        Ui.configureDialog(this);

        for (Path p : AppPrefs.recentVaults()) {
            if (Files.exists(p)) pathBox.addItem(p.toString());
        }
        pathBox.setEditable(true);
        pathBox.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Selecciona tu bóveda (.ckey)");
        if (preselected != null) {
            pathBox.setSelectedItem(preselected.toString());
        } else if (pathBox.getItemCount() > 0) {
            pathBox.setSelectedIndex(0);
        } else {
            pathBox.setSelectedItem("");
        }

        JButton browse = Ui.secondary("Examinar…");
        browse.addActionListener(this::onBrowse);
        JPanel pathRow = new JPanel(new BorderLayout(8, 0));
        pathRow.setOpaque(false);
        pathRow.add(pathBox, BorderLayout.CENTER);
        pathRow.add(browse, BorderLayout.EAST);

        passwordField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Tu clave maestra");
        errorLabel.putClientProperty(FlatClientProperties.STYLE_CLASS, "small");
        if (error != null) errorLabel.setText(error);

        JButton unlock = Ui.primary("Desbloquear", Icons.onFill("lock", 16), this::onOk);
        unlock.putClientProperty(FlatClientProperties.STYLE_CLASS, "large");
        unlock.putClientProperty(FlatClientProperties.STYLE, "minimumHeight: 44");
        getRootPane().setDefaultButton(unlock);

        JButton create = new JButton("Crear una bóveda nueva");
        create.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        create.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.foreground");
        create.setFont(Fonts.regular(13).deriveFont(java.util.Map.of(java.awt.font.TextAttribute.UNDERLINE, java.awt.font.TextAttribute.UNDERLINE_ON)));
        create.addActionListener(this::onNewVault);

        JPanel look = new JPanel(new BorderLayout(10, 0));
        JButton theme = Ui.iconButton(Icons.get(ThemeManager.isDark() ? "sun" : "moon", 18), "Cambiar entre oscuro y claro", e -> {
            ThemeManager.toggle();
            ((JButton) e.getSource()).setIcon(Icons.get(ThemeManager.isDark() ? "sun" : "moon", 18));
            look.removeAll();
            look.add(Ui.muted(ThemeManager.isDark() ? "Takumi oscuro" : "Takumi papel"), BorderLayout.WEST);
            look.add((JButton) e.getSource(), BorderLayout.EAST);
            look.revalidate();
            look.repaint();
        });
        look.setOpaque(false);
        look.add(Ui.muted(ThemeManager.isDark() ? "Takumi oscuro" : "Takumi papel"), BorderLayout.WEST);
        look.add(theme, BorderLayout.EAST);

        JLabel heading = new JLabel("Desbloquea tu bóveda");
        heading.setFont(Fonts.thin(30));

        VBox form = new VBox();
        form.setBorder(BorderFactory.createEmptyBorder(34, 40, 26, 40));
        form.row(Ui.section(0, "Acceso"), 0);
        form.row(heading, 8);
        form.row(Ui.labeled("Bóveda", pathRow), 26);
        form.row(Ui.labeled("Contraseña maestra", passwordField), 14);
        form.row(errorLabel, 8);
        form.row(unlock, 8);
        form.centered(create, 8);
        form.glue();
        form.row(Ui.labeled("Apariencia", look), 12);

        JPanel content = new JPanel(new BorderLayout());
        content.add(new ArtSide(), BorderLayout.WEST);
        content.add(form, BorderLayout.CENTER);
        setContentPane(content);
        setPreferredSize(new Dimension(840, 500));
        pack();
        setLocationRelativeTo(parent);

        boolean hasPath = !pathText().isEmpty();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                (hasPath ? passwordField : pathBox).requestFocusInWindow();
            }
        });
    }



    /** Lado izquierdo: marca, ilustración de línea fina y lema. */
    private static final class ArtSide extends JComponent {
        ArtSide() {
            setPreferredSize(new Dimension(340, 500));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Draw.quality(g);
            Palette p = Draw.p();
            int w = getWidth(), h = getHeight();
            g2.setColor(p.raised());
            g2.fillRect(0, 0, w, h);
            Art.window(g2, Art.Kind.LOCK, 52, 150, 220, 170);
            Draw.cross(g2, 30, 126, 6, p.ink());
            Draw.cross(g2, w - 34, 350, 6, p.ink());
            Art.mark(g2, 36, 30, 44);
            g2.setColor(p.ink());
            g2.setFont(Draw.tracked(Fonts.thin(22), 0.16f));
            g2.drawString("CIPHERKEY", 94, 58);
            g2.setFont(Draw.label());
            g2.setColor(p.muted());
            g2.drawString("TU BÓVEDA CIFRADA", 36, h - 62);
            g2.drawString("100 % OFFLINE · SIN NUBE", 36, h - 44);
            g2.setColor(p.lineHigh());
            g2.fillRect(w - 1, 0, 1, h);
            g2.dispose();
        }
    }

    private String pathText() {
        Object item = pathBox.getEditor().getItem();
        return item == null ? "" : item.toString().trim();
    }

    private void onBrowse(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("Bóvedas CipherKey (*.ckey)", "ckey"));
        fileChooser.setAcceptAllFileFilterUsed(false);
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            pathBox.setSelectedItem(fileChooser.getSelectedFile().getAbsolutePath());
            passwordField.requestFocusInWindow();
        }
    }

    private void onNewVault(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Dónde guardar la nueva bóveda");
        fileChooser.setFileFilter(new FileNameExtensionFilter("Bóvedas CipherKey (*.ckey)", "ckey"));
        fileChooser.setAcceptAllFileFilterUsed(false);
        if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = fileChooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".ckey")) {
            file = new File(file.getAbsolutePath() + ".ckey");
        }
        if (file.exists()) {
            errorLabel.setText("Ya existe un archivo con ese nombre.");
            return;
        }

        CreateVaultDialog dlg = new CreateVaultDialog(this, file.getName());
        char[] password = dlg.showDialog();
        if (password == null) return;
        try {
            Vault.create(file.toPath(), password).close();
            pathBox.setSelectedItem(file.getAbsolutePath());
            errorLabel.setText(" ");
            passwordField.setText("");
            passwordField.requestFocusInWindow();
            Toast.show(this, "Bóveda creada • introduce tu clave para entrar");
        } catch (VaultException ex) {
            errorLabel.setText("No se pudo crear la bóveda: " + ex.getMessage());
        } finally {
            Arrays.fill(password, (char) 0);
        }
    }

    private void onOk(ActionEvent e) {
        String path = pathText();
        if (path.isEmpty()) {
            errorLabel.setText("Selecciona un archivo de bóveda.");
            return;
        }
        Path file = Paths.get(path);
        if (!Files.isRegularFile(file)) {
            errorLabel.setText("El archivo no existe.");
            return;
        }
        if (passwordField.getPassword().length == 0) {
            errorLabel.setText("Escribe tu contraseña maestra.");
            passwordField.requestFocusInWindow();
            return;
        }
        selectedFile = file;
        succeeded = true;
        dispose();
    }

    public boolean showDialog() {
        setVisible(true);
        return succeeded;
    }

    public Path getSelectedFile() {
        return selectedFile;
    }

    /** Copia de la contraseña escrita; el llamador debe borrarla. */
    public char[] getPassword() {
        return passwordField.getPassword();
    }

    public void clearPassword() {
        passwordField.setText("");
    }
}
