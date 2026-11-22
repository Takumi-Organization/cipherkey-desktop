package dev.view;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;

/** Botón de copiar con respuesta visible: tras pulsar, el icono pasa a «✓» durante un momento. */
public class CopyButton extends JButton {
    private static final int FEEDBACK_MS = 1500;
    private final Timer reset = new Timer(FEEDBACK_MS, e -> showIdle());
    private final String idleTip;

    public CopyButton(String tooltip, Runnable action) {
        this.idleTip = tooltip;
        reset.setRepeats(false);
        putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        getAccessibleContext().setAccessibleName(tooltip);
        showIdle();
        addActionListener(e -> {
            action.run();
            setIcon(Icons.ink("check", 16));
            setToolTipText("Copiado");
            reset.restart();
        });
    }

    private void showIdle() {
        setIcon(Icons.get("copy", 16));
        setToolTipText(idleTip);
    }

    @Override
    public void removeNotify() {
        reset.stop();
        super.removeNotify();
    }
}
