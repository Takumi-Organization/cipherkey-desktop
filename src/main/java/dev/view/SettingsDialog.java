package dev.view;

import com.formdev.flatlaf.FlatClientProperties;
import dev.controller.PasswordManager;
import dev.persistence.AppPrefs;
import dev.persistence.VaultException;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.Arrays;

/** Ajustes: bloqueo automático y cambio de clave maestra. */
public class SettingsDialog extends JDialog {
    private static final int[] LOCK_MINUTES = {1, 2, 5, 10, 15, 30};

    private final PasswordManager manager;
    private final JPasswordField currentPwd = Ui.passwordField(20);
    private final JPasswordField newPwd = Ui.passwordField(20);
    private final JPasswordField confirmPwd = Ui.passwordField(20);
    private final StrengthMeter meter = new StrengthMeter();
    private final Ui.ErrorLabel error = new Ui.ErrorLabel();
    private boolean passwordChanged;

    public SettingsDialog(Frame parent, PasswordManager manager) {
        super(parent, "Ajustes", true);
        Ui.configureDialog(this);
        this.manager = manager;

        JComboBox<String> lockBox = new JComboBox<>();
        int selected = 0;
        for (int i = 0; i < LOCK_MINUTES.length; i++) {
            lockBox.addItem(LOCK_MINUTES[i] == 1 ? "1 minuto" : LOCK_MINUTES[i] + " minutos");
            if (LOCK_MINUTES[i] == AppPrefs.autoLockMinutes()) selected = i;
        }
        lockBox.setSelectedIndex(selected);
        lockBox.addActionListener(e -> AppPrefs.setAutoLockMinutes(LOCK_MINUTES[lockBox.getSelectedIndex()]));

        newPwd.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { meter.update(newPwd.getPassword()); }
            public void removeUpdate(DocumentEvent e) { meter.update(newPwd.getPassword()); }
            public void changedUpdate(DocumentEvent e) { }
        });
        error.putClientProperty(FlatClientProperties.STYLE_CLASS, "small");

        VBox body = new VBox();
        body.setBorder(BorderFactory.createEmptyBorder(22, 26, 20, 26));
        JLabel heading = new JLabel("Ajustes");
        heading.setFont(Fonts.thin(28));
        body.row(Ui.section(0, "Apariencia"), 0);
        body.row(heading, 6);
        body.row(Ui.labeled("Tema", modeRow()), 16);
        JCheckBox motion = new JCheckBox("Reducir movimiento (sin animaciones)", AppPrefs.reduceMotion());
        motion.addActionListener(e -> AppPrefs.setReduceMotion(motion.isSelected()));
        body.row(motion, 14);
        body.row(Ui.section(1, "Seguridad"), 26);
        body.row(Ui.labeled("Bloquear tras inactividad", lockBox), 12);
        body.row(Ui.section(2, "Cambiar clave maestra"), 26);
        body.row(Ui.labeled("Clave actual", currentPwd), 12);
        body.row(Ui.labeled("Clave nueva", newPwd), 10);
        body.row(meter, 6);
        body.row(Ui.labeled("Repite la clave nueva", confirmPwd), 10);
        body.row(error, 8);

        JButton close = Ui.secondary("Cerrar");
        close.addActionListener(e -> dispose());
        JButton change = Ui.primary("Cambiar clave", null, e -> changeMasterPassword());
        body.row(Ui.buttonBar(close, change), 14);

        setContentPane(body);
        setMinimumSize(new Dimension(500, 0));
        pack();
        setLocationRelativeTo(parent);
    }


    /** Dos botones conmutables: Takumi oscuro y Takumi papel (el activo va invertido). */
    private JComponent modeRow() {
        JToggleButton darkBtn = new JToggleButton("Takumi oscuro", null, ThemeManager.isDark());
        JToggleButton lightBtn = new JToggleButton("Takumi papel", null, !ThemeManager.isDark());
        // el icono acompaña a la inversión: color de fondo si el botón está activo, tinta suave si no
        darkBtn.setIcon(Icons.colored("moon", 16, () -> darkBtn.isSelected() ? Draw.p().onFill() : Draw.p().dim()));
        lightBtn.setIcon(Icons.colored("sun", 16, () -> lightBtn.isSelected() ? Draw.p().onFill() : Draw.p().dim()));
        ButtonGroup g = new ButtonGroup();
        g.add(darkBtn);
        g.add(lightBtn);
        darkBtn.addActionListener(e -> setMode(true));
        lightBtn.addActionListener(e -> setMode(false));
        JPanel p = new JPanel(new GridLayout(1, 2, 8, 0));
        p.setOpaque(false);
        p.add(darkBtn);
        p.add(lightBtn);
        return p;
    }

    private static void setMode(boolean dark) {
        if (dark == ThemeManager.isDark()) return;
        AppPrefs.setDark(dark);
        ThemeManager.reapply();
    }

    private void changeMasterPassword() {
        char[] oldPwd = currentPwd.getPassword();
        char[] fresh = newPwd.getPassword();
        char[] confirm = confirmPwd.getPassword();
        try {
            if (oldPwd.length == 0) {
                fail("Escribe tu clave actual.", currentPwd);
            } else if (fresh.length < CreateVaultDialog.MIN_LENGTH) {
                fail("La clave nueva necesita al menos " + CreateVaultDialog.MIN_LENGTH + " caracteres.", newPwd);
            } else if (!Arrays.equals(fresh, confirm)) {
                fail("Las claves nuevas no coinciden.", confirmPwd);
            } else {
                manager.changeMasterPassword(oldPwd, fresh);
                passwordChanged = true;
                dispose();
            }
        } catch (VaultException.InvalidPassword ex) {
            fail("La clave actual es incorrecta.", currentPwd);
        } catch (VaultException ex) {
            fail("Error: " + ex.getMessage(), null);
        } finally {
            Arrays.fill(oldPwd, (char) 0);
            Arrays.fill(fresh, (char) 0);
            Arrays.fill(confirm, (char) 0);
        }
    }

    private void fail(String message, JComponent field) {
        error.setText(message);
        if (field != null) Ui.markError(field);
    }

    public boolean isPasswordChanged() {
        return passwordChanged;
    }
}
