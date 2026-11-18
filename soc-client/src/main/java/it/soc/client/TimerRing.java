package it.soc.client;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;

public class TimerRing extends JComponent {

    private final Color accent;

    private long endTime;
    private int duration = 1;
    private boolean running;
    private double pulse;

    public TimerRing(Color accent) {
        this.accent = accent;
        setOpaque(false);
        setPreferredSize(new Dimension(74, 74));

        new Timer(30, e -> {
            pulse += 0.12;
            repaint();
        }).start();
    }

    public void setTime(int secondsLeft, int duration) {
        long end = System.currentTimeMillis() + secondsLeft * 1000L;

        if (!running || Math.abs(end - endTime) > 1200 || this.duration != duration) {
            endTime = end;
        }

        this.duration = Math.max(1, duration);
        running = true;
    }

    public void stop() {
        running = false;
    }

    private double remaining() {
        return running ? Math.max(0, (endTime - System.currentTimeMillis()) / 1000.0) : 0;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int size = Math.min(getWidth(), getHeight()) - 8;
        double x = (getWidth() - size) / 2.0;
        double y = (getHeight() - size) / 2.0;

        double remaining = remaining();
        double ratio = Math.min(1, remaining / duration);
        boolean urgent = running && remaining <= 15;

        Color color = urgent ? Theme.DANGER : remaining <= 45 && running ? Theme.WARN : accent;

        if (urgent) {
            double glow = (Math.sin(pulse) + 1) / 2;
            g2.setColor(Theme.alpha(Theme.DANGER, (int) (30 + glow * 50)));
            g2.fill(new Ellipse2D.Double(x - 4, y - 4, size + 8, size + 8));
        }

        g2.setColor(Theme.PANEL_DARK);
        g2.fill(new Ellipse2D.Double(x, y, size, size));

        g2.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(45, 58, 68));
        g2.draw(new Arc2D.Double(x + 4, y + 4, size - 8, size - 8, 0, 360, Arc2D.OPEN));

        g2.setColor(color);
        g2.draw(new Arc2D.Double(x + 4, y + 4, size - 8, size - 8, 90, ratio * 360, Arc2D.OPEN));

        double angle = Math.toRadians(90 + ratio * 360);
        double r = (size - 8) / 2.0;
        double cx = x + size / 2.0 + Math.cos(angle) * r;
        double cy = y + size / 2.0 - Math.sin(angle) * r;
        g2.setColor(Theme.TEXT);
        g2.fill(new Ellipse2D.Double(cx - 3, cy - 3, 6, 6));

        int seconds = (int) Math.ceil(remaining);
        String text = running ? String.format("%d:%02d", seconds / 60, seconds % 60) : "--:--";

        g2.setFont(Theme.mono(15, Font.BOLD));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(urgent ? Theme.DANGER : Theme.TEXT);
        g2.drawString(text, (float) (getWidth() / 2.0 - fm.stringWidth(text) / 2.0),
                (float) (getHeight() / 2.0 + fm.getAscent() / 2.0 - 2));

        g2.dispose();
    }
}
