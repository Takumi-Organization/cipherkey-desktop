package dev.view;

import com.formdev.flatlaf.FlatClientProperties;
import dev.sync.SyncService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/** Botón de la barra superior para sincronizar: un punto de estado (en curso, pendiente o error) junto al icono. */
final class SyncButton extends JButton {
    private enum Dot { NONE, RUNNING, PENDING, ERROR }

    private Dot dot = Dot.NONE;

    SyncButton(ActionListener action) {
        super(Icons.get("refresh", 18));
        putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        getAccessibleContext().setAccessibleName("Sincronización");
        addActionListener(action);
    }

    void setState(SyncService.Status st) {
        Dot d = st.error() != null ? Dot.ERROR : st.running() ? Dot.RUNNING : st.pending() > 0 ? Dot.PENDING : Dot.NONE;
        if (d != dot) {
            dot = d;
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (dot == Dot.NONE) return;
        Graphics2D g2 = Draw.quality(g);
        Palette p = Draw.p();
        Color c = switch (dot) {
            case ERROR -> p.error();
            case PENDING -> p.warn();
            default -> p.accent();
        };
        int x = getWidth() - 13, y = 6;
        g2.setColor(p.paper());
        g2.fillOval(x - 2, y - 2, 12, 12);
        g2.setColor(c);
        g2.fillOval(x, y, 8, 8);
        g2.dispose();
    }
}
