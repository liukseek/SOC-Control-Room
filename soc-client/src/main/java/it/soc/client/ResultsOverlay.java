package it.soc.client;

import it.soc.common.model.GameResult;
import it.soc.common.model.Role;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ResultsOverlay extends JPanel {

    private final GameResult result;
    private final Color winnerColor;
    private final long opened = System.currentTimeMillis();
    private final Random random = new Random();
    private final List<double[]> particles = new ArrayList<>();

    public ResultsOverlay(GameResult result, Role myRole, String nickname,
                          Runnable onLobby, Runnable onExit) {
        this.result = result;
        this.winnerColor = result.winner() == null ? Theme.STEEL
                : result.winner() == Role.ANALYST ? Theme.GOOD : Theme.DANGER;

        setOpaque(false);
        setLayout(new GridBagLayout());
        addMouseListener(new MouseAdapter() {
        });

        for (int i = 0; i < 70; i++) {
            particles.add(new double[]{
                    random.nextDouble(), random.nextDouble(),
                    0.0005 + random.nextDouble() * 0.0015, 1 + random.nextDouble() * 2.5
            });
        }

        boolean won = result.winner() != null && (result.winner() == Role.INSIDER) == (myRole == Role.INSIDER);
        int scoreRows = result.scores() == null ? 0 : result.scores().size();

        UiKit.RoundedPanel card = new UiKit.RoundedPanel(24, new Color(20, 28, 35, 248),
                Theme.alpha(winnerColor, 150));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(30, 40, 28, 40));
        card.setPreferredSize(new Dimension(900, 640 + (scoreRows == 0 ? 0 : scoreRows * 24 + 36)));

        card.add(UiKit.left(UiKit.label("SOC: CONTROL ROOM  ·  ESITO DELLA SESSIONE", 11, Font.BOLD, Theme.STEEL)));
        card.add(Box.createVerticalStrut(14));

        String title = result.winner() == null ? "SESSIONE INTERROTTA"
                : result.winner() == Role.ANALYST ? "GLI ANALISTI VINCONO" : "L'INSIDER HA VINTO";

        TitleReveal titleReveal = new TitleReveal(title, winnerColor);
        card.add(UiKit.left(titleReveal));
        card.add(Box.createVerticalStrut(6));

        JTextArea reason = ScrollableList.wrap(result.reason(), 15, Font.PLAIN, Theme.TEXT);
        reason.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        card.add(reason);
        card.add(Box.createVerticalStrut(20));

        JPanel reveal = new JPanel(new BorderLayout(20, 0));
        reveal.setOpaque(false);
        reveal.setAlignmentX(Component.LEFT_ALIGNMENT);
        reveal.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        UiKit.RoundedPanel insiderBox = new UiKit.RoundedPanel(14, new Color(48, 28, 30), Theme.alpha(Theme.DANGER, 140));
        insiderBox.setLayout(new BoxLayout(insiderBox, BoxLayout.Y_AXIS));
        insiderBox.setBorder(new EmptyBorder(12, 16, 12, 16));
        insiderBox.add(UiKit.label("L'INSIDER ERA", 10, Font.BOLD, Theme.ERROR));
        insiderBox.add(Box.createVerticalStrut(4));
        insiderBox.add(new ScrambleLabel(result.insider() == null ? "-" : result.insider(), 900));

        JPanel outcome = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 14));
        outcome.setOpaque(false);

        if (result.winner() != null) {
            outcome.add(EvidencePanel.pill(won ? "HAI VINTO" : "HAI PERSO", won ? Theme.GOOD : Theme.ERROR));
        }

        outcome.add(EvidencePanel.pill(myRole == Role.INSIDER ? "ERI L'INSIDER"
                : myRole == Role.FORENSIC ? "ERI IL FORENSE" : "ERI UN ANALISTA",
                Theme.accent(myRole)));

        if (result.ejected() != null) {
            outcome.add(EvidencePanel.pill("ESPULSO: " + result.ejected().toUpperCase(), Theme.WARN));
        }

        reveal.add(insiderBox, BorderLayout.WEST);
        reveal.add(outcome, BorderLayout.CENTER);
        card.add(reveal);
        card.add(Box.createVerticalStrut(20));

        JPanel columns = new JPanel(new GridLayout(1, 2, 20, 0));
        columns.setOpaque(false);
        columns.setAlignmentX(Component.LEFT_ALIGNMENT);
        columns.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        JPanel votes = new JPanel(new BorderLayout(0, 10));
        votes.setOpaque(false);
        votes.add(UiKit.label("VOTAZIONE", 10, Font.BOLD, Theme.MUTED), BorderLayout.NORTH);
        votes.add(new VoteBars(result.votes(), result.insider()), BorderLayout.CENTER);

        JPanel stats = new JPanel(new BorderLayout(0, 10));
        stats.setOpaque(false);
        stats.add(UiKit.label("STATISTICHE", 10, Font.BOLD, Theme.MUTED), BorderLayout.NORTH);

        JPanel tiles = new JPanel(new GridLayout(1, 3, 10, 0));
        tiles.setOpaque(false);
        tiles.add(new StatTile("DATI ESFILTRATI", result.exfiltration(), "%", Theme.DANGER, 300));
        tiles.add(new StatTile("PROVE RACCOLTE", result.evidenceCollected(), "", Theme.STEEL, 450));
        tiles.add(new StatTile("PROVE CONDIVISE", result.evidenceShared(), "", Theme.GOOD, 600));
        stats.add(tiles, BorderLayout.CENTER);

        columns.add(votes);
        columns.add(stats);
        card.add(columns);
        card.add(Box.createVerticalStrut(14));

        if (scoreRows > 0) {
            card.add(UiKit.left(UiKit.label("PUNTEGGI", 10, Font.BOLD, Theme.MUTED)));
            card.add(Box.createVerticalStrut(6));
            card.add(createScoreboard(result, nickname));
            card.add(Box.createVerticalStrut(14));
        }

        card.add(UiKit.left(UiKit.label("COSA HA FATTO L'INSIDER", 10, Font.BOLD, Theme.MUTED)));
        card.add(Box.createVerticalStrut(8));

        ScrollableList timeline = new ScrollableList();
        timeline.setBorder(new EmptyBorder(8, 12, 8, 12));

        List<String> log = result.insiderLog();

        if (log.isEmpty()) {
            timeline.add(UiKit.label("Nessuna azione registrata.", 12, Font.PLAIN, Theme.MUTED));
        }

        for (int i = 0; i < log.size(); i++) {
            JLabel line = new JLabel(log.get(i));
            line.setFont(Theme.mono(12, Font.PLAIN));
            line.setForeground(new Color(0, 0, 0, 0));
            line.setBorder(new EmptyBorder(3, 0, 3, 0));
            timeline.add(line);

            Color target = log.get(i).contains("Esfiltrazione da") ? Theme.ERROR : Theme.mix(Theme.TEXT, Theme.MUTED, 0.3);
            Anim.delay(1200 + i * 140, () -> Anim.tween(400,
                    t -> line.setForeground(Theme.alpha(target, (int) (255 * t))), null));
        }

        JScrollPane timelineScroll = UiKit.scroll(timeline);
        JPanel timelineHolder = new JPanel(new BorderLayout());
        timelineHolder.setBackground(new Color(17, 25, 31));
        timelineHolder.setBorder(BorderFactory.createLineBorder(Theme.BORDER_SOFT));
        timelineHolder.add(timelineScroll);
        timelineHolder.setAlignmentX(Component.LEFT_ALIGNMENT);
        timelineHolder.setPreferredSize(new Dimension(800, scoreRows > 0 ? 100 : 150));
        timelineHolder.setMaximumSize(new Dimension(Integer.MAX_VALUE, scoreRows > 0 ? 120 : 170));
        card.add(timelineHolder);

        card.add(Box.createVerticalGlue());
        card.add(Box.createVerticalStrut(18));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

        JButton exit = new JButton("ESCI");
        JButton lobby = new JButton("TORNA ALLA LOBBY");

        UiKit.styleButton(exit, new Color(55, 65, 73), new Color(75, 87, 96), Theme.TEXT);
        UiKit.styleButton(lobby, Theme.STEEL, Theme.STEEL_HOVER, new Color(20, 29, 36));
        exit.setPreferredSize(new Dimension(110, 44));
        lobby.setPreferredSize(new Dimension(200, 44));
        exit.addActionListener(e -> onExit.run());
        lobby.addActionListener(e -> onLobby.run());

        buttons.add(exit);
        buttons.add(lobby);
        card.add(buttons);

        add(card);

        new Timer(16, e -> repaint()).start();
    }

    private JPanel createScoreboard(GameResult result, String nickname) {
        JPanel board = new JPanel(new GridLayout(0, 1, 0, 4));
        board.setOpaque(false);
        board.setAlignmentX(Component.LEFT_ALIGNMENT);
        board.setMaximumSize(new Dimension(Integer.MAX_VALUE, result.scores().size() * 26));

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(result.scores().entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        for (Map.Entry<String, Integer> entry : sorted) {
            boolean self = entry.getKey().equals(nickname);
            boolean insider = entry.getKey().equals(result.insider());

            JPanel row = new JPanel(new BorderLayout(14, 0));
            row.setOpaque(false);

            String name = entry.getKey() + (self ? " (tu)" : "") + (insider ? "  · insider" : "");
            JLabel nameLabel = UiKit.label(name, 12, Font.BOLD, insider ? Theme.ERROR : self ? Theme.TEXT : Theme.mix(Theme.TEXT, Theme.MUTED, 0.2));
            nameLabel.setPreferredSize(new Dimension(230, 20));

            String record = result.records() == null ? null : result.records().get(entry.getKey());
            JLabel recordLabel = new JLabel(record == null ? "" : record);
            recordLabel.setFont(Theme.mono(11, Font.PLAIN));
            recordLabel.setForeground(Theme.MUTED);

            JLabel points = UiKit.label("+" + entry.getValue(), 13, Font.BOLD, Theme.GOOD);

            row.add(nameLabel, BorderLayout.WEST);
            row.add(recordLabel, BorderLayout.CENTER);
            row.add(points, BorderLayout.EAST);
            board.add(row);
        }

        return board;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        long age = System.currentTimeMillis() - opened;
        float alpha = (float) Math.min(1, age / 500.0);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        int w = getWidth();
        int h = getHeight();

        g2.setColor(new Color(8, 12, 16, 238));
        g2.fillRect(0, 0, w, h);

        g2.setPaint(new RadialGradientPaint(w / 2f, h * 0.35f, Math.max(w, h) * 0.7f,
                new float[]{0f, 1f},
                new Color[]{Theme.alpha(winnerColor, 60), new Color(0, 0, 0, 0)}));
        g2.fillRect(0, 0, w, h);

        for (double[] p : particles) {
            p[1] -= p[2];

            if (p[1] < -0.02) {
                p[1] = 1.02;
                p[0] = random.nextDouble();
            }

            double flicker = (Math.sin(age / 300.0 + p[0] * 40) + 1) / 2;
            g2.setColor(Theme.alpha(winnerColor, (int) (40 + flicker * 90)));
            g2.fillOval((int) (p[0] * w), (int) (p[1] * h), (int) p[3], (int) p[3]);
        }

        g2.dispose();
    }

    private static class TitleReveal extends JComponent {

        private final String text;
        private final Color color;
        private final long start = System.currentTimeMillis();

        TitleReveal(String text, Color color) {
            this.text = text;
            this.color = color;
            setPreferredSize(new Dimension(820, 64));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
            new Timer(16, e -> {
                repaint();

                if (System.currentTimeMillis() - start > 1500) {
                    ((Timer) e.getSource()).stop();
                }
            }).start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            double t = Math.min(1, (System.currentTimeMillis() - start - 200) / 700.0);

            if (t <= 0) {
                g2.dispose();
                return;
            }

            double scale = 0.85 + 0.15 * Anim.easeOutBack(t);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.min(1, t * 1.4)));
            g2.translate(0, getHeight() * 0.75);
            g2.scale(scale, scale);

            g2.setFont(Theme.font(48, Font.BOLD));
            g2.setColor(Theme.alpha(color, 70));
            g2.drawString(text, 2, 3);
            g2.setColor(color);
            g2.drawString(text, 0, 0);

            g2.dispose();
        }
    }

    private class ScrambleLabel extends JComponent {

        private final String text;
        private final int delay;

        ScrambleLabel(String text, int delay) {
            this.text = text;
            this.delay = delay;
            setPreferredSize(new Dimension(220, 34));
            setMaximumSize(new Dimension(320, 34));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            new Timer(40, e -> {
                repaint();

                if (System.currentTimeMillis() - opened > delay + 2500) {
                    ((Timer) e.getSource()).stop();
                }
            }).start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            double progress = Math.max(0, Math.min(1, (System.currentTimeMillis() - opened - delay) / 1100.0));
            int resolved = (int) (text.length() * progress);
            String glyphs = "#$%&@01<>/=+*";
            StringBuilder shown = new StringBuilder();

            for (int i = 0; i < text.length(); i++) {
                shown.append(i < resolved ? text.charAt(i) : glyphs.charAt(random.nextInt(glyphs.length())));
            }

            g2.setFont(Theme.font(24, Font.BOLD));
            g2.setColor(progress >= 1 ? Theme.TEXT : Theme.alpha(Theme.ERROR, 200));
            g2.drawString(shown.toString(), 0, 25);
            g2.dispose();
        }
    }

    private class VoteBars extends JComponent {

        private final Map<String, Integer> votes;
        private final String insider;

        VoteBars(Map<String, Integer> votes, String insider) {
            this.votes = votes;
            this.insider = insider;
            setPreferredSize(new Dimension(400, 120));
            new Timer(16, e -> {
                repaint();

                if (System.currentTimeMillis() - opened > 3000) {
                    ((Timer) e.getSource()).stop();
                }
            }).start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            if (votes == null || votes.isEmpty()) {
                g2.setFont(Theme.font(12, Font.PLAIN));
                g2.setColor(Theme.MUTED);
                g2.drawString("Nessuna votazione: la partita si è conclusa prima.", 0, 20);
                g2.dispose();
                return;
            }

            int max = 1;

            for (int value : votes.values()) {
                max = Math.max(max, value);
            }

            int rows = votes.size();
            int rowHeight = Math.min(26, getHeight() / Math.max(1, rows));
            int labelWidth = 120;
            int y = 0;
            int index = 0;

            for (var entry : votes.entrySet()) {
                double t = Anim.easeOutCubic(Math.max(0, Math.min(1,
                        (System.currentTimeMillis() - opened - 600 - index * 120) / 700.0)));

                boolean isInsider = entry.getKey().equals(insider);
                Color color = isInsider ? Theme.DANGER
                        : entry.getKey().equals(result.ejected()) ? Theme.WARN : Theme.STEEL;

                g2.setFont(Theme.font(11, Font.BOLD));
                g2.setColor(isInsider ? Theme.ERROR : Theme.TEXT);
                g2.drawString(entry.getKey(), 0, y + rowHeight / 2 + 4);

                int barMax = getWidth() - labelWidth - 30;
                int barWidth = (int) (barMax * entry.getValue() / (double) max * t);

                g2.setColor(new Color(40, 52, 61));
                g2.fill(new RoundRectangle2D.Double(labelWidth, y + rowHeight / 2.0 - 5, barMax, 10, 10, 10));
                g2.setColor(color);
                g2.fill(new RoundRectangle2D.Double(labelWidth, y + rowHeight / 2.0 - 5, Math.max(barWidth, 0), 10, 10, 10));

                g2.setFont(Theme.mono(11, Font.BOLD));
                g2.setColor(Theme.TEXT);
                g2.drawString(String.valueOf(entry.getValue()), labelWidth + barMax + 10, y + rowHeight / 2 + 4);

                y += rowHeight;
                index++;
            }

            g2.dispose();
        }
    }

    private class StatTile extends JComponent {

        private final String title;
        private final int value;
        private final String suffix;
        private final Color color;
        private final int delay;

        StatTile(String title, int value, String suffix, Color color, int delay) {
            this.title = title;
            this.value = value;
            this.suffix = suffix;
            this.color = color;
            this.delay = delay;
            setPreferredSize(new Dimension(120, 96));
            new Timer(16, e -> {
                repaint();

                if (System.currentTimeMillis() - opened > delay + 2000) {
                    ((Timer) e.getSource()).stop();
                }
            }).start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            double t = Anim.easeOutCubic(Math.max(0, Math.min(1,
                    (System.currentTimeMillis() - opened - delay) / 1000.0)));

            g2.setColor(Theme.PANEL_LIGHT);
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 14, 14));
            g2.setColor(color);
            g2.fillRect(14, 0, 36, 3);

            g2.setFont(Theme.font(9, Font.BOLD));
            g2.setColor(Theme.MUTED);
            g2.drawString(title, 14, 26);

            g2.setFont(Theme.font(32, Font.BOLD));
            g2.setColor(Theme.TEXT);
            g2.drawString(Math.round(value * t) + suffix, 14, 70);

            g2.dispose();
        }
    }
}
