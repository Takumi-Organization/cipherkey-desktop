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
            String text = switch (d) {
                case ERROR -> "Sincronización: error";
                case RUNNING -> "Sincronizando…";
                case PENDING -> "Sincronización: cambios pendientes";
                default -> "Sincronización";
            };
            setToolTipText(text);
            getAccessibleContext().setAccessibleName(text);
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (dot == Dot.NONE) return;
        Graphics2D g2 = Draw.quality(g);
        Palette p = Draw.p();
        // la insignia se distingue por FORMA, no por color: ✕ error (cuadrado invertido), ▲ pendiente, ○ en curso
        int x = getWidth() - 15, y = 4;
        g2.setColor(p.paper());
        g2.fillRect(x - 2, y - 2, 14, 14);
        g2.setColor(p.ink());
        switch (dot) {
            case ERROR -> {
                g2.fillRect(x, y, 10, 10);
                g2.setColor(p.paper());
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawLine(x + 3, y + 3, x + 7, y + 7);
                g2.drawLine(x + 7, y + 3, x + 3, y + 7);
            }
            case PENDING -> g2.fill(new java.awt.geom.Path2D.Float() {{
                moveTo(x + 5, y); lineTo(x + 10, y + 10); lineTo(x, y + 10); closePath();
            }});
            default -> {
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(x + 1, y + 1, 8, 8);
            }
        }
        g2.dispose();
    }
}
