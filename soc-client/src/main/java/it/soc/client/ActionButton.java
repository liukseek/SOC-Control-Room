package it.soc.client;

import it.soc.common.model.ActionType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;

public class ActionButton extends JComponent {

    private final ActionType action;
    private final String title;
    private final String hint;
    private final Color accent;

    private Runnable onClick;
    private boolean available = true;
    private boolean hovered;
    private double hoverAnim;
    private double pressAnim;
    private double readyFlash;
    private long cooldownEnd;
    private double cooldownTotal = 1;
    private boolean wasCooling;

    public ActionButton(ActionType action, String title, String hint, Color accent) {
        this.action = action;
        this.title = title;
        this.hint = hint;
        this.accent = accent;

        setOpaque(false);
        setPreferredSize(new Dimension(180, 58));
        setMinimumSize(new Dimension(130, 58));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hovered = true;
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = false;
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (isReady() && onClick != null) {
                    pressAnim = 1;
                    onClick.run();
                }
            }
        });

        new Timer(30, e -> animate()).start();
    }

    public ActionType action() {
        return action;
    }

    public void setOnClick(Runnable onClick) {
        this.onClick = onClick;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void setCooldown(int seconds) {
        long now = System.currentTimeMillis();

        if (seconds <= 0) {
            if (cooldownEnd - now > 1200) {
                cooldownEnd = now;
            }
            return;
        }

        long end = now + seconds * 1000L;
        double remaining = Math.max(0, (cooldownEnd - now) / 1000.0);

        if (remaining <= 0.05 || seconds > remaining + 1.5) {
            cooldownTotal = seconds;
        }

        if (Math.abs(end - cooldownEnd) > 1100) {
            cooldownEnd = end;
        }
    }

    private double remaining() {
        return Math.max(0, (cooldownEnd - System.currentTimeMillis()) / 1000.0);
    }

    public boolean isReady() {
        return available && remaining() <= 0;
    }

    private void animate() {
        hoverAnim = Anim.approach(hoverAnim, hovered && isReady() ? 1 : 0, 0.2);
        pressAnim = Math.max(0, pressAnim - 0.06);
        readyFlash = Math.max(0, readyFlash - 0.03);

        boolean cooling = remaining() > 0;

        if (wasCooling && !cooling) {
            readyFlash = 1;
        }

        wasCooling = cooling;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        double remaining = remaining();
        boolean cooling = remaining > 0;
        boolean enabled = available && !cooling;

        double inset = pressAnim * 2;
        RoundRectangle2D shape = new RoundRectangle2D.Double(inset, inset, w - 1 - inset * 2, h - 1 - inset * 2, 14, 14);

        Color base = Theme.mix(Theme.PANEL_LIGHT, accent, 0.10 + hoverAnim * 0.18 + readyFlash * 0.25);

        if (!available) {
            base = Theme.PANEL_DARK;
        }

        g2.setColor(base);
        g2.fill(shape);

        if (cooling) {
            double ratio = Math.min(1, remaining / Math.max(1, cooldownTotal));
            Graphics2D clip = (Graphics2D) g2.create();
            clip.clip(shape);
            clip.setColor(new Color(10, 15, 20, 150));
            clip.fill(new Rectangle2D.Double(0, 0, w * ratio, h));
            clip.setColor(Theme.alpha(accent, 160));
            clip.fill(new Rectangle2D.Double(w * ratio - 2, 0, 2, h));
            clip.dispose();
        }

        g2.setColor(Theme.alpha(accent, enabled ? (int) (120 + hoverAnim * 135) : 60));
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(shape);

        if (readyFlash > 0) {
            g2.setColor(Theme.alpha(accent, (int) (readyFlash * 200)));
            g2.setStroke(new BasicStroke(2.5f));
            g2.draw(shape);
        }

        double iconSize = 34;
        double ix = 12;
        double iy = (h - iconSize) / 2;

        g2.setColor(Theme.alpha(accent, enabled ? 60 : 25));
        g2.fill(new Ellipse2D.Double(ix, iy, iconSize, iconSize));

        Color iconColor = enabled ? Theme.mix(Theme.TEXT, accent, 0.3) : Theme.alpha(Theme.MUTED, 120);
        drawGlyph(g2, ix + iconSize / 2, iy + iconSize / 2, iconColor);

        int textX = (int) (ix + iconSize + 11);

        g2.setFont(Theme.font(12, Font.BOLD));
        g2.setColor(enabled ? Theme.TEXT : Theme.alpha(Theme.MUTED, 170));
        g2.drawString(title, textX, h / 2 - 2);

        g2.setFont(Theme.font(10, Font.PLAIN));
        g2.setColor(Theme.alpha(Theme.MUTED, enabled ? 255 : 140));

        String sub = cooling ? "In ricarica..." : (available ? hint : "Non disponibile");
        g2.drawString(sub, textX, h / 2 + 13);

        if (cooling) {
            String countdown = (int) Math.ceil(remaining) + "s";
            g2.setFont(Theme.mono(15, Font.BOLD));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(accent);
            g2.drawString(countdown, w - fm.stringWidth(countdown) - 12, h / 2 + 5);
        }

        g2.dispose();
    }

    private void drawGlyph(Graphics2D g, double cx, double cy, Color color) {
        Graphics2D icon = (Graphics2D) g.create();
        icon.translate(cx, cy);
        icon.setColor(color);
        icon.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        switch (action) {
            case ANALYZE -> {
                icon.draw(new Ellipse2D.Double(-7, -7, 11, 11));
                icon.draw(new Line2D.Double(3, 3, 8, 8));
            }
            case ISOLATE -> {
                icon.draw(new RoundRectangle2D.Double(-7, -1, 14, 10, 3, 3));
                icon.draw(new Arc2D.Double(-4.5, -9, 9, 13, 0, 180, Arc2D.OPEN));
            }
            case EXFILTRATE -> {
                icon.draw(new Line2D.Double(0, 7, 0, -7));
                icon.draw(new Line2D.Double(0, -7, -5, -2));
                icon.draw(new Line2D.Double(0, -7, 5, -2));
                icon.draw(new Line2D.Double(-8, 9, 8, 9));
            }
            case WIPE -> {
                Path2D eraser = new Path2D.Double();
                eraser.moveTo(-8, 3);
                eraser.lineTo(1, -6);
                eraser.lineTo(8, 1);
                eraser.lineTo(2, 7);
                eraser.lineTo(-4, 7);
                eraser.closePath();
                icon.draw(eraser);
                icon.draw(new Line2D.Double(-3, -2, 4, 5));
            }
            case FRAME -> {
                icon.draw(new Ellipse2D.Double(-7, -7, 14, 14));
                icon.draw(new Line2D.Double(0, -10, 0, -4));
                icon.draw(new Line2D.Double(0, 4, 0, 10));
                icon.draw(new Line2D.Double(-10, 0, -4, 0));
                icon.draw(new Line2D.Double(4, 0, 10, 0));
            }
        }

        icon.dispose();
    }
}
