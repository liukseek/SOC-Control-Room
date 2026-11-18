package it.soc.client;

import it.soc.common.model.Role;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class ThreatMeter extends JComponent {

    private static final int SEGMENTS = 20;

    private final Role role;

    private double shown;
    private int target;
    private double flash;
    private double phase;

    public ThreatMeter(Role role) {
        this.role = role;
        setOpaque(false);
        setPreferredSize(new Dimension(290, 52));

        new Timer(30, e -> {
            shown = Anim.approach(shown, target, 0.08);
            flash = Math.max(0, flash - 0.02);
            phase += 0.1;
            repaint();
        }).start();
    }

    public void setValue(int value) {
        if (value > target) {
            flash = 1;
        }

        target = value;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();

        String label = role == Role.INSIDER ? "OBIETTIVO ESFILTRAZIONE" : "DATI ESFILTRATI";
        g2.setFont(Theme.font(10, Font.BOLD));
        g2.setColor(Theme.MUTED);
        g2.drawString(label, 0, 12);

        String value = Math.round(shown) + "%";
        g2.setFont(Theme.mono(16, Font.BOLD));
        FontMetrics fm = g2.getFontMetrics();
        Color valueColor = shown >= 70 ? Theme.DANGER : shown >= 40 ? Theme.WARN : Theme.TEXT;
        g2.setColor(Theme.mix(valueColor, Color.WHITE, flash * 0.6));
        g2.drawString(value, w - fm.stringWidth(value), 14);

        double gap = 3;
        double segmentWidth = (w - gap * (SEGMENTS - 1)) / SEGMENTS;
        double y = 24;
        double h = 16;

        for (int i = 0; i < SEGMENTS; i++) {
            double x = i * (segmentWidth + gap);
            double fill = Math.max(0, Math.min(1, shown / 100.0 * SEGMENTS - i));
            double position = i / (double) (SEGMENTS - 1);

            Color segment = position < 0.4
                    ? Theme.mix(Theme.STEEL, Theme.WARN, position / 0.4)
                    : Theme.mix(Theme.WARN, Theme.DANGER, (position - 0.4) / 0.6);

            g2.setColor(new Color(40, 52, 61));
            g2.fill(new RoundRectangle2D.Double(x, y, segmentWidth, h, 4, 4));

            if (fill > 0) {
                double shimmer = (Math.sin(phase - i * 0.5) + 1) / 2;
                g2.setColor(Theme.mix(segment, Color.WHITE, flash * 0.5 + shimmer * 0.12));
                g2.fill(new RoundRectangle2D.Double(x, y, segmentWidth * fill, h, 4, 4));
            }
        }

        g2.dispose();
    }
}
