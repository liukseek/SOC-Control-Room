package it.soc.client;

import it.soc.common.model.Role;
import it.soc.common.model.RoleInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BriefingOverlay extends JComponent {

    private static final String GLYPHS = "#$%&@01<>/\\=+*?XZ";

    private final RoleInfo role;
    private final Color accent;
    private final Random random = new Random();
    private final long opened = System.currentTimeMillis();
    private final Timer timer;

    private Runnable onClose;
    private boolean closing;
    private long closeStart;
    private long endTime;
    private int duration = 1;
    private Rectangle buttonBounds = new Rectangle();
    private boolean buttonHover;

    public BriefingOverlay(RoleInfo role) {
        this.role = role;
        this.accent = Theme.accent(role.role());
        setOpaque(false);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                buttonHover = buttonBounds.contains(e.getPoint()) && age() > 2600;
                setCursor(Cursor.getPredefinedCursor(buttonHover ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (buttonBounds.contains(e.getPoint()) && age() > 2600) {
                    dismiss();
                }
            }
        };

        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        timer = new Timer(16, e -> repaint());
        timer.start();
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    public void setCountdown(int secondsLeft, int duration) {
        long end = System.currentTimeMillis() + secondsLeft * 1000L;

        if (Math.abs(end - endTime) > 1200) {
            endTime = end;
        }

        this.duration = Math.max(1, duration);
    }

    public void dismiss() {
        if (closing) {
            return;
        }

        closing = true;
        closeStart = System.currentTimeMillis();

        Anim.delay(450, () -> {
            timer.stop();
            setVisible(false);

            if (onClose != null) {
                onClose.run();
            }
        });
    }

    private long age() {
        return System.currentTimeMillis() - opened;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        long age = age();
        double fadeIn = Math.min(1, age / 400.0);
        double fadeOut = closing ? Math.min(1, (System.currentTimeMillis() - closeStart) / 420.0) : 0;
        float alpha = (float) Math.max(0, fadeIn * (1 - fadeOut));

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        int w = getWidth();
        int h = getHeight();

        g2.setColor(new Color(8, 12, 16, 235));
        g2.fillRect(0, 0, w, h);

        g2.setPaint(new RadialGradientPaint(w / 2f, h / 2f, Math.max(w, h) * 0.6f,
                new float[]{0f, 1f},
                new Color[]{Theme.alpha(accent, 45), new Color(0, 0, 0, 0)}));
        g2.fillRect(0, 0, w, h);

        g2.setColor(new Color(255, 255, 255, 6));

        for (int y = 0; y < h; y += 3) {
            g2.drawLine(0, y, w, y);
        }

        int band = (int) ((age / 6) % (h + 200)) - 100;
        g2.setColor(Theme.alpha(accent, 14));
        g2.fillRect(0, band, w, 60);

        int cardW = Math.min(680, w - 80);
        int cardH = Math.min(560, h - 60);
        int cx = (w - cardW) / 2;
        int cy = (h - cardH) / 2;
        double rise = (1 - Anim.easeOutCubic(fadeIn)) * 30 + fadeOut * -20;

        g2.translate(cx, cy + rise);

        RoundRectangle2D card = new RoundRectangle2D.Double(0, 0, cardW, cardH, 22, 22);
        g2.setColor(new Color(20, 28, 35, 245));
        g2.fill(card);
        g2.setColor(Theme.alpha(accent, 150));
        g2.setStroke(new BasicStroke(1.3f));
        g2.draw(card);

        g2.setColor(accent);
        g2.fillRect(40, 0, 70, 3);

        int x = 44;
        int y = 54;

        String header = "BRIEFING CLASSIFICATO  ·  POSTAZIONE " + role.workstation();
        int typed = (int) Math.min(header.length(), age / 18);
        g2.setFont(Theme.mono(11, Font.BOLD));
        g2.setColor(Theme.MUTED);
        g2.drawString(header.substring(0, typed) + (typed < header.length() || (age / 400) % 2 == 0 ? "_" : ""),
                x, y);

        y += 40;
        g2.setFont(Theme.font(13, Font.BOLD));
        g2.setColor(Theme.STEEL);
        g2.drawString("ASSEGNAZIONE RUOLO", x, y);

        y += 62;
        drawRoleTitle(g2, x, y, age);

        double divider = Anim.easeOutCubic(Math.max(0, Math.min(1, (age - 1700) / 500.0)));
        y += 26;
        g2.setColor(Theme.alpha(accent, 160));
        g2.fillRect(x, y, (int) ((cardW - x * 2) * divider), 1);

        y += 28;
        float textAlpha = (float) Math.max(0, Math.min(1, (age - 1800) / 450.0));

        if (textAlpha > 0) {
            Graphics2D text = (Graphics2D) g2.create();
            text.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * textAlpha));
            text.setFont(Theme.font(14, Font.PLAIN));
            text.setColor(Theme.TEXT);

            for (String line : wrap(text.getFontMetrics(), role.briefing(), cardW - x * 2)) {
                text.drawString(line, x, y);
                y += 21;
            }

            text.dispose();
        }

        y += 16;
        g2.setFont(Theme.font(10, Font.BOLD));
        g2.setColor(Theme.alpha(Theme.MUTED, (int) (255 * textAlpha)));
        g2.drawString("OBIETTIVI", x, y);
        y += 22;

        List<String> objectives = role.objectives();

        for (int i = 0; i < objectives.size(); i++) {
            double t = Math.max(0, Math.min(1, (age - 2200 - i * 260) / 380.0));

            if (t <= 0) {
                continue;
            }

            double eased = Anim.easeOutCubic(t);
            Graphics2D item = (Graphics2D) g2.create();
            item.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (alpha * t)));
            item.translate((1 - eased) * -20, 0);

            item.setColor(Theme.alpha(accent, 60));
            item.fillRoundRect(x, y - 13, 22, 18, 5, 5);
            item.setFont(Theme.mono(10, Font.BOLD));
            item.setColor(accent);
            item.drawString(String.format("%02d", i + 1), x + 4, y);

            item.setFont(Theme.font(13, Font.PLAIN));
            item.setColor(Theme.TEXT);
            item.drawString(objectives.get(i), x + 34, y);
            item.dispose();

            y += 28;
        }

        int bottom = cardH - 40;
        double remaining = Math.max(0, (endTime - System.currentTimeMillis()) / 1000.0);
        double ratio = Math.min(1, remaining / duration);

        g2.setFont(Theme.font(11, Font.BOLD));
        g2.setColor(Theme.MUTED);
        String countdown = remaining > 0
                ? "L'INDAGINE INIZIA TRA " + (int) Math.ceil(remaining) + " S"
                : "INDAGINE IN AVVIO...";
        g2.drawString(countdown, x, bottom - 18);

        int barWidth = cardW - x * 2 - 190;
        g2.setColor(new Color(40, 52, 61));
        g2.fillRoundRect(x, bottom - 6, barWidth, 6, 6, 6);
        g2.setColor(accent);
        g2.fillRoundRect(x, bottom - 6, (int) (barWidth * ratio), 6, 6, 6);

        float buttonAlpha = (float) Math.max(0, Math.min(1, (age - 2600) / 400.0));
        int bw = 160;
        int bh = 42;
        int bx = cardW - x - bw;
        int by = bottom - bh + 2;

        buttonBounds = new Rectangle((int) (cx + bx), (int) (cy + rise + by), bw, bh);

        Graphics2D button = (Graphics2D) g2.create();
        button.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * buttonAlpha));
        button.setColor(buttonHover ? Theme.mix(accent, Color.WHITE, 0.2) : accent);
        button.fillRoundRect(bx, by, bw, bh, 10, 10);
        button.setFont(Theme.font(12, Font.BOLD));
        button.setColor(new Color(20, 29, 36));
        FontMetrics fm = button.getFontMetrics();
        String label = "SONO PRONTO";
        button.drawString(label, bx + (bw - fm.stringWidth(label)) / 2, by + bh / 2 + 5);
        button.dispose();

        g2.dispose();
    }

    private void drawRoleTitle(Graphics2D g2, int x, int y, long age) {
        String title = role.title();
        double progress = Math.max(0, Math.min(1, (age - 500) / 1200.0));
        int resolved = (int) (title.length() * progress);

        StringBuilder text = new StringBuilder();

        for (int i = 0; i < title.length(); i++) {
            if (i < resolved || title.charAt(i) == ' ') {
                text.append(title.charAt(i));
            } else if (age > 350) {
                text.append(GLYPHS.charAt(random.nextInt(GLYPHS.length())));
            }
        }

        g2.setFont(Theme.font(54, Font.BOLD));

        if (progress < 1) {
            int jitter = random.nextInt(5) - 2;
            g2.setColor(new Color(220, 80, 80, 90));
            g2.drawString(text.toString(), x - 3 + jitter, y);
            g2.setColor(new Color(80, 200, 220, 90));
            g2.drawString(text.toString(), x + 3 - jitter, y);
        }

        g2.setColor(progress < 1 ? Theme.TEXT : accent);
        g2.drawString(text.toString(), x, y);

        if (progress >= 1) {
            FontMetrics fm = g2.getFontMetrics();
            int tx = x + fm.stringWidth(title) + 18;

            String tag = role.role() == Role.INSIDER ? "MINACCIA INTERNA" : "DIFESA";
            g2.setFont(Theme.font(10, Font.BOLD));
            FontMetrics tm = g2.getFontMetrics();
            g2.setColor(Theme.alpha(accent, 50));
            g2.fillRoundRect(tx, y - 30, tm.stringWidth(tag) + 18, 22, 22, 22);
            g2.setColor(accent);
            g2.drawRoundRect(tx, y - 30, tm.stringWidth(tag) + 18, 22, 22, 22);
            g2.drawString(tag, tx + 9, y - 15);
        }
    }

    private static List<String> wrap(FontMetrics fm, String text, int width) {
        List<String> lines = new ArrayList<>();
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

        return lines;
    }
}
