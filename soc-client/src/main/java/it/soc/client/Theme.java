package it.soc.client;

import it.soc.common.model.Role;

import java.awt.Color;
import java.awt.Font;

public final class Theme {

    public static final Color BACKGROUND = new Color(13, 20, 26);
    public static final Color BACKGROUND_LIGHT = new Color(27, 40, 49);
    public static final Color PANEL = new Color(24, 33, 41);
    public static final Color PANEL_LIGHT = new Color(31, 43, 52);
    public static final Color PANEL_DARK = new Color(18, 26, 32);
    public static final Color STEEL = new Color(112, 142, 162);
    public static final Color STEEL_HOVER = new Color(145, 169, 185);
    public static final Color TEXT = new Color(237, 242, 245);
    public static final Color MUTED = new Color(155, 170, 181);
    public static final Color BORDER = new Color(65, 80, 91);
    public static final Color BORDER_SOFT = new Color(51, 66, 77);
    public static final Color GOOD = new Color(137, 181, 169);
    public static final Color WARN = new Color(214, 178, 110);
    public static final Color ERROR = new Color(220, 145, 125);
    public static final Color DANGER = new Color(214, 96, 86);
    public static final Color CRIMSON = new Color(178, 64, 60);
    public static final Color HONEY = new Color(150, 196, 128);

    private Theme() {
    }

    public static Color accent(Role role) {
        return role == Role.INSIDER ? DANGER : STEEL;
    }

    public static Color accentSoft(Role role) {
        return role == Role.INSIDER ? new Color(64, 34, 36) : new Color(38, 57, 64);
    }

    public static Color severity(String severity) {
        if (severity == null) {
            return MUTED;
        }

        return switch (severity) {
            case "CRITICAL" -> DANGER;
            case "HIGH" -> ERROR;
            case "MEDIUM" -> WARN;
            case "SUCCESS" -> GOOD;
            case "WARN" -> WARN;
            case "LOW" -> new Color(120, 150, 168);
            default -> STEEL;
        };
    }

    public static Color alpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.max(0, Math.min(255, alpha)));
    }

    public static Color mix(Color a, Color b, double t) {
        double k = Math.max(0, Math.min(1, t));

        return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * k),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * k),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * k),
                (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * k)
        );
    }

    public static Color playerColor(String name) {
        Color[] palette = {
                new Color(143, 186, 205),
                new Color(170, 196, 150),
                new Color(214, 178, 110),
                new Color(190, 160, 210),
                new Color(120, 190, 180),
                new Color(222, 160, 140),
                new Color(160, 176, 222),
                new Color(205, 190, 140)
        };

        return palette[Math.floorMod(name.hashCode(), palette.length)];
    }

    public static Font font(int size, int style) {
        return new Font("Arial", style, size);
    }

    public static Font mono(int size, int style) {
        return new Font("Monospaced", style, size);
    }
}
