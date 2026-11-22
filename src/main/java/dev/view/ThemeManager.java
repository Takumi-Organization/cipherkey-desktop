package dev.view;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import dev.persistence.AppPrefs;

import javax.swing.*;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Tema Takumi sobre FlatLaf, en un único punto: oscuro (por defecto) y claro («papel»). La elección se guarda en
 * {@link AppPrefs} (solo oscuro/claro; las preferencias de acento de versiones anteriores se ignoran). La estructura
 * (radios 0, bordes de 1 px, medidas) está en {@code dev/view/themes/FlatLaf.properties}; aquí van los colores.
 */
public final class ThemeManager {
    private static Palette current = Palette.DARK;

    private ThemeManager() {}

    /** Instala el Look & Feel según la preferencia guardada. Llamar antes de crear ventanas. */
    public static void install() {
        FlatLaf.registerCustomDefaultsSource("dev.view.themes");
        apply();
    }

    /** Paleta activa (los componentes con pintado propio la leen al pintar, así cambian solos con el tema). */
    public static Palette palette() {
        return current;
    }

    public static boolean isDark() {
        return current.dark();
    }

    /** Cambia entre oscuro y claro con la animación de FlatLaf. */
    public static void toggle() {
        AppPrefs.setDark(!isDark());
        reapply();
    }

    /** Aplica de nuevo las preferencias (tema) a la interfaz abierta. */
    public static void reapply() {
        boolean animate = Motion.enabled();
        if (animate) FlatAnimatedLafChange.showSnapshot();
        apply();
        FlatLaf.updateUI();
        if (animate) FlatAnimatedLafChange.hideSnapshotWithAnimation();
    }

    private static void apply() {
        boolean dark = AppPrefs.isDark();
        current = Palette.of(dark);
        FlatLaf.setPreferredFontFamily(Fonts.install());
        FlatLaf.setPreferredMonospacedFontFamily(Fonts.mono(13).getFamily());
        UIManager.put("defaultFont", Fonts.regular(14));
        FlatLaf.setGlobalExtraDefaults(defaults(current));
        if (dark) FlatDarkLaf.setup();
        else FlatLightLaf.setup();
    }

    /** Fondo de los campos de texto: «#050505» en oscuro (tokens de Takumi) y el panel blanco en claro. */
    static Color fieldBackground(Palette p) {
        return p.dark() ? new Color(0x050505) : p.raised();
    }

    static Map<String, String> defaults(Palette p) {
        Map<String, String> m = new HashMap<>();
        String clear = "#00000000";
        m.put("@background", hex(p.paper()));
        m.put("@foreground", hex(p.ink()));
        m.put("@accentColor", hex(p.ink()));
        // selección = inversión: fondo tinta, texto fondo
        m.put("@selectionBackground", hex(p.fill()));
        m.put("@selectionForeground", hex(p.onFill()));
        m.put("@selectionInactiveBackground", hex(p.dim()));
        m.put("@selectionInactiveForeground", hex(p.onFill()));
        m.put("@componentBackground", hex(fieldBackground(p)));
        m.put("Component.borderColor", hex(p.lineHigh()));
        m.put("Component.disabledBorderColor", hex(p.line()));
        m.put("Component.focusColor", hex(p.ink()));
        m.put("Component.focusedBorderColor", hex(p.ink()));
        // el error y el aviso no se pintan de color: borde de tinta (el texto «✕ …» / «▲ …» lo acompaña)
        m.put("Component.error.borderColor", hex(p.ink()));
        m.put("Component.error.focusedBorderColor", hex(p.ink()));
        m.put("Component.warning.borderColor", hex(p.dim()));
        m.put("Component.warning.focusedBorderColor", hex(p.dim()));
        // botón secundario: solo borde; el primario (inversión) lo pinta TakumiButtonUI
        m.put("Button.background", hex(p.paper()));
        m.put("Button.foreground", hex(p.ink()));
        m.put("Button.borderColor", hex(p.lineHigh()));
        m.put("Button.hoverBorderColor", hex(p.ink()));
        m.put("Button.hoverBackground", hex(p.paper()));
        m.put("Button.pressedBackground", hex(p.high()));
        m.put("Button.focusedBorderColor", hex(p.ink()));
        m.put("Button.disabledBorderColor", hex(p.line()));
        m.put("Button.disabledText", hex(p.faint()));
        m.put("Button.default.background", hex(p.fill()));
        m.put("Button.default.foreground", hex(p.onFill()));
        m.put("Button.default.borderColor", clear);
        m.put("Button.default.hoverBorderColor", clear);
        m.put("Button.default.focusedBorderColor", clear);
        m.put("Button.default.hoverBackground", hex(p.dim()));
        m.put("Button.default.pressedBackground", hex(p.muted()));
        m.put("Button.default.focusedBackground", hex(p.fill()));
        m.put("Button.default.focusColor", hex(p.onFill()));
        m.put("Button.toolbar.hoverBackground", hex(p.high()));
        m.put("Button.toolbar.pressedBackground", hex(p.line()));
        m.put("ToggleButton.background", hex(p.paper()));
        m.put("ToggleButton.foreground", hex(p.ink()));
        m.put("ToggleButton.selectedBackground", hex(p.fill()));
        m.put("ToggleButton.selectedForeground", hex(p.onFill()));
        m.put("ToggleButton.hoverBackground", hex(p.high()));
        m.put("ToggleButton.pressedBackground", hex(p.high()));
        m.put("ToggleButton.disabledSelectedBackground", hex(p.lineHigh()));
        m.put("ToggleButton.toolbar.selectedBackground", hex(p.fill()));
        for (String k : new String[]{"TextField", "PasswordField", "FormattedTextField", "TextArea", "TextPane", "EditorPane",
                "ComboBox", "Spinner"}) {
            m.put(k + ".background", hex(fieldBackground(p)));
        }
        m.put("ComboBox.buttonBackground", hex(fieldBackground(p)));
        m.put("ComboBox.buttonArrowColor", hex(p.dim()));
        m.put("ComboBox.buttonSeparatorColor", hex(p.lineHigh()));
        m.put("ComboBox.buttonEditableBackground", hex(fieldBackground(p)));
        m.put("ComboBox.popupBackground", hex(p.raised()));
        m.put("ComboBox.selectionBackground", hex(p.fill()));
        m.put("ComboBox.selectionForeground", hex(p.onFill()));
        m.put("List.background", hex(p.paper()));
        m.put("List.selectionBackground", hex(p.fill()));
        m.put("List.selectionForeground", hex(p.onFill()));
        m.put("Table.background", hex(p.paper()));
        m.put("Table.gridColor", hex(p.line()));
        m.put("PopupMenu.background", hex(p.raised()));
        m.put("PopupMenu.borderColor", hex(p.lineHigh()));
        m.put("MenuItem.background", hex(p.raised()));
        m.put("MenuItem.selectionBackground", hex(p.fill()));
        m.put("MenuItem.selectionForeground", hex(p.onFill()));
        m.put("MenuItem.acceleratorForeground", hex(p.muted()));
        m.put("MenuItem.acceleratorSelectionForeground", hex(p.onFill()));
        m.put("Separator.foreground", hex(p.line()));
        m.put("ToolTip.background", hex(p.high()));
        m.put("ToolTip.foreground", hex(p.ink()));
        m.put("ToolTip.borderColor", hex(p.lineHigh()));
        m.put("ScrollBar.thumb", hex(p.lineHigh()));
        m.put("ScrollBar.hoverThumbColor", hex(p.muted()));
        m.put("ScrollBar.pressedThumbColor", hex(p.dim()));
        m.put("ScrollBar.track", clear);
        m.put("ProgressBar.background", hex(p.lineHigh()));
        m.put("ProgressBar.foreground", hex(p.ink()));
        m.put("Slider.trackColor", hex(p.lineHigh()));
        m.put("Slider.thumbColor", hex(p.ink()));
        m.put("Slider.trackValueColor", hex(p.ink()));
        m.put("Slider.focusedColor", hex(p.ink()));
        m.put("CheckBox.icon.background", hex(fieldBackground(p)));
        m.put("CheckBox.icon.borderColor", hex(p.muted()));
        m.put("CheckBox.icon.selectedBackground", hex(p.fill()));
        m.put("CheckBox.icon.selectedBorderColor", hex(p.fill()));
        m.put("CheckBox.icon.checkmarkColor", hex(p.onFill()));
        m.put("CheckBox.icon.focusedBorderColor", hex(p.ink()));
        m.put("CheckBox.icon.hoverBorderColor", hex(p.ink()));
        m.put("Label.disabledForeground", hex(p.faint()));
        m.put("TextComponent.disabledForeground", hex(p.faint()));
        m.put("TextField.placeholderForeground", hex(p.muted()));
        m.put("TextField.caretForeground", hex(p.ink()));
        m.put("OptionPane.background", hex(p.paper()));
        m.put("Panel.background", hex(p.paper()));
        m.put("TitlePane.background", hex(p.paper()));
        // claves propias
        m.put("App.sidebar.background", hex(p.raised()));
        m.put("App.card.background", hex(p.raised()));
        m.put("App.card.border", hex(p.lineHigh()));
        m.put("App.icon.color", hex(p.dim()));
        m.put("App.muted", hex(p.muted()));
        m.put("App.fill", hex(p.fill()));
        m.put("App.onFill", hex(p.onFill()));
        return m;
    }

    private static String hex(Color c) {
        return Palette.toHex(c);
    }
}
