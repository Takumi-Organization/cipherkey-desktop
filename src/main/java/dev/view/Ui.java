package dev.view;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/** Pequeños constructores de componentes con el estilo de la aplicación. */
public final class Ui {
    private Ui() {}

    /** Título de diálogo: Outfit 200 (fino), como los títulos de Takumi. */
    public static JLabel title(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Fonts.thin(26));
        return l;
    }

    /** Texto pequeño y apagado (etiquetas de campo, ayudas). */
    public static JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.putClientProperty(FlatClientProperties.STYLE_CLASS, "small");
        l.putClientProperty(FlatClientProperties.STYLE, "foreground: $App.muted");
        return l;
    }

    /** Micro-etiqueta de Takumi: JetBrains Mono, mayúsculas, espaciada (etiquetas de campo y de sección). */
    public static JLabel eyebrow(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(Draw.label());
        l.putClientProperty(FlatClientProperties.STYLE, "foreground: $App.muted");
        return l;
    }

    /** Título de sección: micro-etiqueta en mayúsculas (el número se conserva en la firma por compatibilidad, no se pinta). */
    public static JLabel section(int n, String text) {
        return eyebrow(text);
    }

    /** Fuente de las etiquetas de botón: Outfit 500 en mayúsculas espaciadas. */
    static Font buttonFont() {
        return Draw.tracked(Fonts.bold(12), 0.12f);
    }

    /** Botón con icono, sin borde, para barras de herramientas y acciones inline. */
    public static JButton iconButton(Icon icon, String tooltip, ActionListener action) {
        JButton b = new JButton(icon);
        b.setToolTipText(tooltip);
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        b.setFocusable(false);
        b.addActionListener(action);
        return b;
    }

    /** Botón primario: inversión (fondo tinta, texto fondo), esquina cortada y etiqueta en mayúsculas espaciada. */
    public static JButton primary(String text, Icon icon, ActionListener action) {
        JButton b = new JButton(text == null ? null : text.toUpperCase(), icon);
        b.putClientProperty(TakumiButtonUI.PRIMARY, Boolean.TRUE);
        b.setFont(buttonFont());
        b.setIconTextGap(10);
        b.addActionListener(action);
        return b;
    }

    /** Botón secundario: solo borde de línea alta (el borde pasa a tinta al pasar el ratón), etiqueta en mayúsculas. */
    public static JButton secondary(String text) {
        JButton b = new JButton(text.toUpperCase());
        b.setFont(buttonFont());
        return b;
    }

    public static JButton secondary(String text, Icon icon, ActionListener action) {
        JButton b = secondary(text);
        b.setIcon(icon);
        b.setIconTextGap(10);
        b.addActionListener(action);
        return b;
    }

    /** Acción destructiva: secundario con borde de tinta (Takumi no usa rojo). */
    public static JButton danger(String text, Icon icon, ActionListener action) {
        JButton b = secondary(text, icon, action);
        b.putClientProperty(FlatClientProperties.STYLE, "borderColor: $App.fill");
        return b;
    }

    /** Mensaje de error: icono ✕ (alerta) + texto en negrita; el icono solo aparece si hay texto. */
    public static final class ErrorLabel extends JLabel {
        public ErrorLabel() {
            super(" ");
            putClientProperty(FlatClientProperties.STYLE_CLASS, "small");
            setFont(Fonts.bold(12));
            setIconTextGap(8);
        }

        @Override
        public void setText(String text) {
            super.setText(text);
            boolean has = text != null && !text.isBlank();
            setIcon(has ? Icons.get("x", 14) : null);
        }
    }

    /** Etiqueta pequeña encima del componente, apilados en vertical. */
    public static JPanel labeled(String label, JComponent component) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        p.add(eyebrow(label), BorderLayout.NORTH);
        p.add(component, BorderLayout.CENTER);
        return p;
    }

    /** Marca un campo con error (borde de tinta; el mensaje «✕ …» va aparte) hasta que el usuario lo edite. */
    public static void markError(JComponent c) {
        c.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR);
        c.requestFocusInWindow();
    }

    public static void clearError(JComponent c) {
        c.putClientProperty(FlatClientProperties.OUTLINE, null);
    }

    public static JPanel buttonBar(JComponent... buttons) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        p.setOpaque(false);
        for (JComponent b : buttons) p.add(b);
        return p;
    }

    public static JPasswordField passwordField(int columns) {
        JPasswordField f = new JPasswordField(columns);
        f.putClientProperty(FlatClientProperties.STYLE, "showRevealButton: true");
        return f;
    }

    public static void configureDialog(JDialog d) {
        d.setIconImage(Icons.appIcon());
        d.setResizable(false);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /** Escapa texto que viene de fuera (p. ej. el nombre de otro dispositivo) antes de mostrarlo en una etiqueta HTML de Swing. */
    static String escapeHtml(String t) {
        return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
