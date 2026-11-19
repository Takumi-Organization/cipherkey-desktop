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

/** Tema Ryoku Glass sobre FlatLaf: oscuro/claro + acento. La elección se guarda en {@link AppPrefs}. */
public final class ThemeManager {
    private static Palette current = Palette.resolve(true, Palette.DEFAULT_ACCENT, null);

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

    /** Aplica de nuevo las preferencias (tema y acento) a la interfaz abierta. */
    public static void reapply() {
        boolean animate = Motion.enabled();
        if (animate) FlatAnimatedLafChange.showSnapshot();
        apply();
        FlatLaf.updateUI();
        if (animate) FlatAnimatedLafChange.hideSnapshotWithAnimation();
    }

    private static void apply() {
        boolean dark = AppPrefs.isDark();
        current = Palette.resolve(dark, AppPrefs.accent(), AppPrefs.customAccent());
        FlatLaf.setPreferredFontFamily(Fonts.install());
        FlatLaf.setPreferredMonospacedFontFamily(Fonts.mono(13).getFamily());
        UIManager.put("defaultFont", Fonts.regular(13));
        FlatLaf.setGlobalExtraDefaults(defaults(current));
        if (dark) FlatDarkLaf.setup();
        else FlatLightLaf.setup();
    }

    private static Map<String, String> defaults(Palette p) {
        Map<String, String> m = new HashMap<>();
        m.put("@background", hex(p.paper()));
        m.put("@foreground", hex(p.ink()));
        m.put("@accentColor", hex(p.accent()));
        m.put("@selectionBackground", hex(p.high()));
        m.put("@selectionForeground", hex(p.ink()));
        m.put("@selectionInactiveBackground", hex(p.high()));
        m.put("@selectionInactiveForeground", hex(p.ink()));
        m.put("@componentBackground", hex(p.raised()));
        m.put("Component.borderColor", argb(p.line2()));
        m.put("Component.disabledBorderColor", argb(p.line1()));
        m.put("Component.focusColor", hex(p.accent()));
        m.put("Component.focusedBorderColor", hex(p.accent()));
        m.put("Component.error.borderColor", hex(p.error()));
        m.put("Component.error.focusedBorderColor", hex(p.error()));
        m.put("Component.warning.borderColor", hex(p.warn()));
        m.put("Button.background", hex(p.paper()));
        m.put("Button.foreground", hex(p.ink()));
        m.put("Button.borderColor", argb(p.line3()));
        m.put("Button.hoverBackground", hex(p.high()));
        m.put("Button.pressedBackground", hex(p.high()));
        m.put("Button.focusedBorderColor", hex(p.accent()));
        m.put("Button.default.background", hex(p.accent()));
        m.put("Button.default.foreground", hex(p.onAccent()));
        m.put("Button.default.borderColor", hex(p.accent()));
        m.put("Button.default.hoverBackground", hex(Palette.mix(p.accent(), p.ink(), 0.12)));
        m.put("Button.default.pressedBackground", hex(Palette.mix(p.accent(), p.paper(), 0.18)));
        m.put("Button.default.focusedBackground", hex(p.accent()));
        m.put("Button.default.focusColor", hex(p.ink()));
        m.put("Button.toolbar.hoverBackground", hex(p.high()));
        m.put("Button.toolbar.pressedBackground", hex(Palette.mix(p.high(), p.ink(), 0.1)));
        for (String k : new String[]{"TextField", "PasswordField", "FormattedTextField", "TextArea", "TextPane", "EditorPane",
                "ComboBox", "Spinner"}) {
            m.put(k + ".background", hex(p.raised()));
        }
        m.put("ComboBox.buttonBackground", hex(p.raised()));
        m.put("ComboBox.buttonArrowColor", hex(p.dim()));
        m.put("ComboBox.popupBackground", hex(p.raised()));
        m.put("List.background", hex(p.paper()));
        m.put("Table.background", hex(p.paper()));
        m.put("Table.gridColor", argb(p.line1()));
        m.put("PopupMenu.background", hex(p.raised()));
        m.put("PopupMenu.borderColor", argb(p.line2()));
        m.put("MenuItem.background", hex(p.raised()));
        m.put("MenuItem.selectionBackground", hex(p.high()));
        m.put("MenuItem.selectionForeground", hex(p.ink()));
        m.put("MenuItem.acceleratorForeground", hex(p.muted()));
        m.put("Separator.foreground", argb(p.line1()));
        m.put("ToolTip.background", hex(p.high()));
        m.put("ToolTip.foreground", hex(p.ink()));
        m.put("ToolTip.borderColor", argb(p.line2()));
        m.put("ScrollBar.thumb", argb(p.line2()));
        m.put("ScrollBar.hoverThumbColor", argb(p.line3()));
        m.put("ScrollBar.pressedThumbColor", hex(p.muted()));
        m.put("ScrollBar.track", "#00000000");
        m.put("ProgressBar.background", hex(p.high()));
        m.put("ProgressBar.foreground", hex(p.accent()));
        m.put("Slider.trackColor", hex(p.high()));
        m.put("Slider.thumbColor", hex(p.accent()));
        m.put("Slider.trackValueColor", hex(p.accent()));
        m.put("CheckBox.icon.selectedBackground", hex(p.accent()));
        m.put("CheckBox.icon.selectedBorderColor", hex(p.accent()));
        m.put("CheckBox.icon.checkmarkColor", hex(p.onAccent()));
        m.put("Label.disabledForeground", hex(p.faint()));
        m.put("TextComponent.disabledForeground", hex(p.faint()));
        m.put("TextField.placeholderForeground", hex(p.faint()));
        m.put("OptionPane.background", hex(p.paper()));
        m.put("Panel.background", hex(p.paper()));
        // claves propias
        m.put("App.sidebar.background", hex(p.raised()));
        m.put("App.card.background", hex(p.raised()));
        m.put("App.card.border", argb(p.line1()));
        m.put("App.icon.color", hex(p.dim()));
        m.put("App.muted", hex(p.muted()));
        m.put("App.success", hex(p.ok()));
        m.put("App.warning", hex(p.warn()));
        m.put("App.danger", hex(p.error()));
        m.put("App.accent", hex(p.accent()));
        m.put("App.onAccent", hex(p.onAccent()));
        return m;
    }

    private static String hex(Color c) {
        return Palette.toHex(c);
    }

    /** #RRGGBBAA: así lee FlatLaf el color con transparencia. */
    private static String argb(Color c) {
        return String.format("#%02X%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
    }
}
