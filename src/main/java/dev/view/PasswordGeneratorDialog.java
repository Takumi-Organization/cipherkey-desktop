package dev.view;

import dev.security.PasswordGenerator;
import dev.security.SecureClipboard;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;

public class PasswordGeneratorDialog extends JDialog {
    private final PasswordGenerator generator = new PasswordGenerator();
    private final JCheckBox upperChk = new JCheckBox("Mayúsculas (A-Z)", true);
    private final JCheckBox lowerChk = new JCheckBox("Minúsculas (a-z)", true);
    private final JCheckBox digitsChk = new JCheckBox("Números (0-9)", true);
    private final JCheckBox symbolsChk = new JCheckBox("Símbolos (!@#…)", true);
    private final JCheckBox ambiguousChk = new JCheckBox("Evitar caracteres ambiguos (I l 1 O 0)");
    private final JSlider lengthSlider = new JSlider(8, 64, 20);
    private final JLabel lengthLabel = new JLabel();
    private final PasswordView result = new PasswordView(20, true);
    private final StrengthMeter meter = new StrengthMeter();

    private String lastGenerated = "";
    private boolean accepted;

    /** @param selectMode true si se abre desde un formulario y debe devolver la contraseña ("Usar"); false para copiar. */
    public PasswordGeneratorDialog(Window parent, boolean selectMode) {
        super(parent, "Generador de contraseñas", ModalityType.APPLICATION_MODAL);
        Ui.configureDialog(this);

        JPanel resultBox = new JPanel(new BorderLayout(10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Draw.quality(g);
                Draw.box(g2, Draw.p().raised(), Draw.p().ink(), 0, 0, getWidth(), getHeight(), Draw.CUT_MD);
                g2.dispose();
            }
        };
        resultBox.setOpaque(false);
        resultBox.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 10));
        resultBox.add(result, BorderLayout.CENTER);
        JPanel tools = new JPanel(new GridLayout(0, 1, 0, 4));
        tools.setOpaque(false);
        tools.add(Ui.iconButton(Icons.get("refresh", 16), "Generar otra", e -> generate()));
        tools.add(new CopyButton("Copiar", this::copy));
        JPanel toolsWrap = new JPanel(new BorderLayout());
        toolsWrap.setOpaque(false);
        toolsWrap.add(tools, BorderLayout.NORTH);
        resultBox.add(toolsWrap, BorderLayout.EAST);

        lengthSlider.addChangeListener(e -> {
            lengthLabel.setText(lengthSlider.getValue() + " caracteres");
            if (!lengthSlider.getValueIsAdjusting()) generate();
        });
        for (JCheckBox c : new JCheckBox[]{upperChk, lowerChk, digitsChk, symbolsChk, ambiguousChk}) {
            c.addItemListener(this::onOptionChanged);
        }

        JPanel lengthRow = new JPanel(new BorderLayout(12, 0));
        lengthRow.setOpaque(false);
        lengthRow.add(lengthSlider, BorderLayout.CENTER);
        lengthRow.add(lengthLabel, BorderLayout.EAST);
        lengthLabel.setPreferredSize(new Dimension(96, 20));

        JPanel classes = new JPanel(new GridLayout(2, 2, 12, 6));
        classes.setOpaque(false);
        classes.add(upperChk);
        classes.add(lowerChk);
        classes.add(digitsChk);
        classes.add(symbolsChk);

        VBox body = new VBox();
        body.setBorder(BorderFactory.createEmptyBorder(22, 26, 20, 26));
        JLabel heading = new JLabel("Generador de contraseñas");
        heading.setFont(Fonts.thin(28));
        body.row(Ui.section(0, "Generador"), 0);
        body.row(heading, 6);
        body.row(resultBox, 16);
        body.row(meter, 12);
        body.row(Ui.eyebrow("Longitud"), 20);
        body.row(lengthRow, 4);
        body.row(Ui.eyebrow("Caracteres"), 14);
        body.row(classes, 6);
        body.row(ambiguousChk, 8);

        JButton close = Ui.secondary(selectMode ? "Cancelar" : "Cerrar");
        close.addActionListener(e -> dispose());
        JButton action;
        if (selectMode) {
            action = Ui.primary("Usar contraseña", null, e -> {
                accepted = true;
                dispose();
            });
        } else {
            action = Ui.primary("Copiar", Icons.onFill("copy", 16), e -> copy());
        }
        getRootPane().setDefaultButton(action);
        body.row(Ui.buttonBar(close, action), 20);

        setContentPane(body);
        lengthLabel.setText(lengthSlider.getValue() + " caracteres");
        generate();
        setMinimumSize(new Dimension(500, 0));
        pack();
        setLocationRelativeTo(parent);
    }


    private void onOptionChanged(ItemEvent e) {
        // Siempre debe quedar al menos un tipo de carácter seleccionado.
        if (!upperChk.isSelected() && !lowerChk.isSelected() && !digitsChk.isSelected() && !symbolsChk.isSelected()) {
            ((JCheckBox) e.getSource()).setSelected(true);
            return;
        }
        generate();
    }

    private void generate() {
        generator.setIncludeUppercase(upperChk.isSelected());
        generator.setIncludeLowercase(lowerChk.isSelected());
        generator.setIncludeDigits(digitsChk.isSelected());
        generator.setIncludeSymbols(symbolsChk.isSelected());
        generator.setExcludeAmbiguous(ambiguousChk.isSelected());
        generator.setLength(lengthSlider.getValue());
        lastGenerated = generator.generate();
        result.setPassword(lastGenerated, true);
        meter.update(lastGenerated);
    }

    private void copy() {
        if (!lastGenerated.isEmpty()) {
            SecureClipboard.copyToClipboard(lastGenerated);
            Toast.show(this, "Copiada • se borrará en 30 s");
        }
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getGeneratedPassword() {
        return lastGenerated;
    }
}
