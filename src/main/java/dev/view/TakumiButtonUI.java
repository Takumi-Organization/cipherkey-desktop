package dev.view;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatButtonUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * Botón de Takumi. El primario (botón por defecto o marcado con {@link #PRIMARY}) es inversión de colores —fondo
 * tinta, texto fondo— con la esquina superior derecha cortada en diagonal; lo pinta esta clase porque FlatLaf solo
 * sabe de rectángulos. El secundario es solo borde y lo resuelve FlatLaf con radio 0 (ver FlatLaf.properties).
 */
public final class TakumiButtonUI extends FlatButtonUI {
    /** Propiedad de cliente: {@code Boolean.TRUE} para pintar el botón como primario. */
    public static final String PRIMARY = "tk.primary";

    private TakumiButtonUI() {
        super(false);
    }

    public static ComponentUI createUI(JComponent c) {
        return new TakumiButtonUI();
    }

    private static boolean primary(JComponent c) {
        return c instanceof AbstractButton b
                && b.getClientProperty(FlatClientProperties.BUTTON_TYPE) == null
                && (Boolean.TRUE.equals(b.getClientProperty(PRIMARY)) || (b instanceof JButton jb && jb.isDefaultButton()));
    }

    @Override
    public void update(Graphics g, JComponent c) {
        if (!primary(c)) {
            super.update(g, c);
            return;
        }
        AbstractButton b = (AbstractButton) c;
        Palette p = Draw.p();
        // texto de la inversión: el color del fondo (el botón por defecto ya lo trae de «Button.default.foreground»)
        if (b.isEnabled() && !p.onFill().equals(b.getForeground())) b.setForeground(p.onFill());
        Graphics2D g2 = Draw.quality(g);
        Color fill = !b.isEnabled() ? p.high()
                : b.getModel().isPressed() ? p.muted()
                : b.getModel().isRollover() ? p.dim() : p.fill();
        int w = c.getWidth(), h = c.getHeight();
        Draw.box(g2, fill, b.isEnabled() ? null : p.lineHigh(), 0, 0, w, h, h <= 40 ? Draw.CUT_SM : Draw.CUT_MD);
        if (b.isFocusOwner()) {
            // foco: filete interior de color de fondo (el borde exterior ya es tinta)
            g2.setColor(p.onFill());
            g2.setStroke(new BasicStroke(1f));
            g2.drawRect(3, 3, w - 7, h - 7);
        }
        g2.dispose();
        paint(g, c);
    }
}
