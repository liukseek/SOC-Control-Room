package it.soc.client;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;

public class AnimatedBackdrop extends JPanel {

    private final Color tint;
    private final Timer timer;
    private double phase;
    private double alarm;
    private double alarmTarget;

    public AnimatedBackdrop(Color tint) {
        this.tint = tint;
        setOpaque(true);
        setBackground(Theme.BACKGROUND);
        setDoubleBuffered(true);

        timer = new Timer(40, e -> {
            phase = (phase + 0.016) % (Math.PI * 2);
            alarm = Anim.approach(alarm, alarmTarget, 0.05);
            repaint();
        });
    }

    public void setAlarm(double level) {
        alarmTarget = Math.max(0, Math.min(1, level));
    }

    @Override
    public void addNotify() {
        super.addNotify();
        timer.start();
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        g2.setPaint(new GradientPaint(0, 0, Theme.BACKGROUND_LIGHT, w, h, Theme.BACKGROUND));
        g2.fillRect(0, 0, w, h);

        float pulse = (float) (0.5 + 0.5 * Math.sin(phase));
        int alpha = 9 + (int) (pulse * 9);

        g2.setColor(Theme.alpha(tint, alpha));
        g2.fill(new Ellipse2D.Double(w * 0.55, h * 0.1, w * 0.5, h * 0.6));

        if (alarm > 0.01) {
            double beat = (Math.sin(phase * 6) + 1) / 2;
            g2.setPaint(new RadialGradientPaint(w / 2f, h / 2f, Math.max(w, h) * 0.75f,
                    new float[]{0.55f, 1f},
                    new Color[]{new Color(0, 0, 0, 0),
                            Theme.alpha(Theme.DANGER, (int) (alarm * (25 + beat * 35)))}));
            g2.fillRect(0, 0, w, h);
        }

        g2.setColor(new Color(130, 160, 180, 10));

        for (int x = 0; x < w; x += 50) {
            g2.drawLine(x, 0, x, h);
        }

        for (int y = 0; y < h; y += 50) {
            g2.drawLine(0, y, w, y);
        }

        int y = (int) (phase / (Math.PI * 2) * h);
        g2.setPaint(new GradientPaint(0, y - 12, Theme.alpha(tint, 0), 0, y, Theme.alpha(tint, 16)));
        g2.fillRect(0, y - 12, w, 12);

        g2.dispose();
    }
}
