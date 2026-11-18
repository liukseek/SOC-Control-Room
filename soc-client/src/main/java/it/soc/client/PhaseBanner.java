package it.soc.client;

import javax.swing.*;
import java.awt.*;

public class PhaseBanner extends JComponent {

    private static final int DURATION = 2300;

    private String title;
    private String subtitle;
    private Color color = Theme.STEEL;
    private long started;
    private final Timer timer;

    public PhaseBanner() {
        setOpaque(false);
        timer = new Timer(16, e -> {
            if (System.currentTimeMillis() - started > DURATION) {
                ((Timer) e.getSource()).stop();
                title = null;
            }

            repaint();
        });
    }

    public void show(String title, String subtitle, Color color) {
        this.title = title;
        this.subtitle = subtitle;
        this.color = color;
        this.started = System.currentTimeMillis();
        timer.restart();
    }

    @Override
    public boolean contains(int x, int y) {
        return false;
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (title == null) {
            return;
        }

        long age = System.currentTimeMillis() - started;
        double in = Math.min(1, age / 350.0);
        double out = age > DURATION - 450 ? Math.min(1, (age - (DURATION - 450)) / 450.0) : 0;
        float alpha = (float) Math.max(0, Math.min(1, in * (1 - out)));

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        int w = getWidth();
        int h = getHeight();
        int bandHeight = 130;
        int y = h / 2 - bandHeight / 2;

        g2.setPaint(new LinearGradientPaint(0, 0, w, 0,
                new float[]{0f, 0.2f, 0.8f, 1f},
                new Color[]{
                        new Color(10, 15, 20, 0),
                        new Color(10, 15, 20, 225),
                        new Color(10, 15, 20, 225),
                        new Color(10, 15, 20, 0)
                }));
        g2.fillRect(0, y, w, bandHeight);

        double lineProgress = Anim.easeOutCubic(Math.min(1, age / 600.0));
        int lineWidth = (int) (w * 0.6 * lineProgress);
        g2.setColor(color);
        g2.fillRect(w / 2 - lineWidth / 2, y, lineWidth, 2);
        g2.fillRect(w / 2 - lineWidth / 2, y + bandHeight - 2, lineWidth, 2);

        double scale = 0.92 + 0.08 * Anim.easeOutBack(in);
        g2.translate(w / 2.0, h / 2.0);
        g2.scale(scale, scale);

        g2.setFont(Theme.font(44, Font.BOLD));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Theme.TEXT);
        g2.drawString(title, -fm.stringWidth(title) / 2f, 6);

        if (subtitle != null) {
            g2.setFont(Theme.font(14, Font.PLAIN));
            fm = g2.getFontMetrics();
            g2.setColor(color);
            g2.drawString(subtitle, -fm.stringWidth(subtitle) / 2f, 38);
        }

        g2.dispose();
    }
}
