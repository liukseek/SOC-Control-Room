package it.soc.client;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ToastLayer extends JComponent {

    private static final int WIDTH = 330;
    private static final int LIFETIME = 4800;

    private final List<Toast> toasts = new ArrayList<>();
    private final Timer timer;
    private int topOffset = 120;

    public ToastLayer() {
        setOpaque(false);
        timer = new Timer(16, e -> tick());
    }

    public void setTopOffset(int topOffset) {
        this.topOffset = topOffset;
    }

    public void show(String title, String text, Color color) {
        toasts.add(0, new Toast(title, text, color, System.currentTimeMillis()));

        while (toasts.size() > 4) {
            toasts.remove(toasts.size() - 1);
        }

        if (!timer.isRunning()) {
            timer.start();
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Toast> iterator = toasts.iterator();

        while (iterator.hasNext()) {
            if (now - iterator.next().created > LIFETIME + 400) {
                iterator.remove();
            }
        }

        if (toasts.isEmpty()) {
            timer.stop();
        }

        repaint();
    }

    @Override
    public boolean contains(int x, int y) {
        return false;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        long now = System.currentTimeMillis();
        double y = topOffset;

        for (Toast toast : toasts) {
            long age = now - toast.created;
            double in = Anim.easeOutBack(Math.min(1, age / 380.0));
            double out = age > LIFETIME ? Math.min(1, (age - LIFETIME) / 400.0) : 0;
            float alpha = (float) Math.max(0, Math.min(1, Math.min(1, age / 200.0) * (1 - out)));

            List<String> lines = wrap(g2.getFontMetrics(Theme.font(11, Font.PLAIN)), toast.text, WIDTH - 52);
            double height = 34 + lines.size() * 15;

            toast.y = toast.y == 0 ? y : Anim.approach(toast.y, y, 0.25);

            double x = getWidth() - 24 - WIDTH + (1 - in) * (WIDTH + 30) + out * 40;

            Graphics2D t = (Graphics2D) g2.create();
            t.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            t.translate(x, toast.y);

            RoundRectangle2D shape = new RoundRectangle2D.Double(0, 0, WIDTH, height, 14, 14);

            t.setColor(new Color(0, 0, 0, 90));
            t.fill(new RoundRectangle2D.Double(3, 5, WIDTH, height, 14, 14));
            t.setColor(new Color(22, 31, 38, 245));
            t.fill(shape);
            t.setColor(Theme.alpha(toast.color, 170));
            t.draw(shape);

            t.setColor(toast.color);
            t.fill(new Ellipse2D.Double(16, 15, 9, 9));
            t.setColor(Theme.alpha(toast.color, 60));
            t.fill(new Ellipse2D.Double(12, 11, 17, 17));

            t.setFont(Theme.font(11, Font.BOLD));
            t.setColor(toast.color);
            t.drawString(toast.title.toUpperCase(), 38, 23);

            t.setFont(Theme.font(11, Font.PLAIN));
            t.setColor(Theme.TEXT);

            int lineY = 41;

            for (String line : lines) {
                t.drawString(line, 38, lineY);
                lineY += 15;
            }

            double life = Math.min(1, age / (double) LIFETIME);
            t.setColor(Theme.alpha(toast.color, 120));
            t.fill(new RoundRectangle2D.Double(14, height - 4, (WIDTH - 28) * (1 - life), 2, 2, 2));

            t.dispose();

            y += height + 10;
        }

        g2.dispose();
    }

    private static List<String> wrap(FontMetrics fm, String text, int width) {
        List<String> lines = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return lines;
        }

        StringBuilder line = new StringBuilder();

        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;

            if (fm.stringWidth(candidate) > width && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }

        if (!line.isEmpty()) {
            lines.add(line.toString());
        }

        if (lines.size() > 3) {
            List<String> cut = new ArrayList<>(lines.subList(0, 3));
            cut.set(2, cut.get(2) + "...");
            return cut;
        }

        return lines;
    }

    private static class Toast {

        final String title;
        final String text;
        final Color color;
        final long created;
        double y;

        Toast(String title, String text, Color color, long created) {
            this.title = title;
            this.text = text;
            this.color = color;
            this.created = created;
        }
    }
}
