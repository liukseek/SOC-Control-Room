package it.soc.client;

import it.soc.common.model.PlayerInfo;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class VotePanel extends JPanel {

    private final String nickname;
    private final Color accent;
    private final JPanel grid = new JPanel(new GridLayout(0, 3, 12, 12));
    private final Map<String, VoteCard> cards = new LinkedHashMap<>();
    private final JLabel progressLabel = UiKit.label("0/0 HANNO VOTATO", 11, Font.BOLD, Theme.STEEL);
    private final JLabel statusLabel = UiKit.label("Seleziona l'operatore che ritieni sia l'insider.",
            12, Font.PLAIN, Theme.MUTED);
    private final JButton skipButton = new JButton("ASTIENITI");
    private final JButton confirmButton = new JButton("CONFERMA VOTO");

    private Consumer<String> voteListener;
    private String selected;
    private String submitted;
    private String rosterKey = "";
    private boolean meExpelled;

    public VotePanel(String nickname, Color accent) {
        this.nickname = nickname;
        this.accent = accent;

        setOpaque(false);
        setLayout(new BorderLayout(0, 16));

        add(UiKit.cardHeader("Chi è l'insider?",
                "Rivedi le prove, discuti nel canale e vota. Decide la maggioranza.", progressLabel),
                BorderLayout.NORTH);

        grid.setOpaque(false);

        ScrollableList gridHolder = new ScrollableList();
        gridHolder.setLayout(new BorderLayout(0, 18));
        gridHolder.add(grid, BorderLayout.NORTH);
        gridHolder.add(createTips(), BorderLayout.CENTER);

        add(UiKit.scroll(gridHolder), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(12, 0));
        bottom.setOpaque(false);

        UiKit.styleButton(skipButton, new Color(55, 65, 73), new Color(75, 87, 96), Theme.TEXT);
        UiKit.styleButton(confirmButton, accent, Theme.mix(accent, Color.WHITE, 0.2), new Color(20, 29, 36));
        skipButton.setPreferredSize(new Dimension(120, 42));
        confirmButton.setPreferredSize(new Dimension(170, 42));
        confirmButton.setEnabled(false);

        skipButton.addActionListener(e -> submit(""));
        confirmButton.addActionListener(e -> {
            if (selected != null) {
                submit(selected);
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        buttons.add(skipButton);
        buttons.add(confirmButton);

        bottom.add(statusLabel, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.EAST);

        add(bottom, BorderLayout.SOUTH);
    }

    private JPanel createTips() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);

        UiKit.RoundedPanel tips = new UiKit.RoundedPanel(14, Theme.PANEL_LIGHT, Theme.BORDER_SOFT);
        tips.setLayout(new BoxLayout(tips, BoxLayout.Y_AXIS));
        tips.setBorder(new EmptyBorder(16, 18, 16, 18));

        tips.add(UiKit.left(UiKit.label("PRIMA DI VOTARE", 10, Font.BOLD, Theme.MUTED)));
        tips.add(Box.createVerticalStrut(12));

        String[][] lines = {
                {"01", "Le prove VERIFICATE vengono dai log reali, ma l'insider può aver piazzato tracce false."},
                {"02", "Una rotazione manuale dei log indica che qualcuno ha cancellato delle tracce."},
                {"03", "Il registro badge e i report SIEM restringono il campo dei sospetti."},
                {"04", "In caso di parità o se prevalgono gli astenuti nessuno viene espulso: vince l'insider."}
        };

        for (String[] line : lines) {
            JPanel row = new JPanel(new BorderLayout(12, 0));
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel number = new JLabel(line[0]);
            number.setFont(Theme.mono(12, Font.BOLD));
            number.setForeground(accent);
            number.setVerticalAlignment(SwingConstants.TOP);

            row.add(number, BorderLayout.WEST);
            row.add(ScrollableList.wrap(line[1], 12, Font.PLAIN, Theme.mix(Theme.TEXT, Theme.MUTED, 0.25)),
                    BorderLayout.CENTER);

            tips.add(row);
            tips.add(Box.createVerticalStrut(9));
        }

        wrapper.add(tips, BorderLayout.NORTH);
        return wrapper;
    }

    public void setVoteListener(Consumer<String> listener) {
        this.voteListener = listener;
    }

    public void reset(boolean emergency) {
        selected = null;
        submitted = null;
        confirmButton.setEnabled(false);
        skipButton.setEnabled(!meExpelled);

        statusLabel.setForeground(emergency ? Theme.WARN : Theme.MUTED);
        statusLabel.setText(emergency
                ? "Voto di emergenza: espellere un innocente fa salire l'esfiltrazione del 10%."
                : "Seleziona l'operatore che ritieni sia l'insider.");

        for (VoteCard card : cards.values()) {
            card.repaint();
        }
    }

    private void submit(String vote) {
        if (voteListener == null) {
            return;
        }

        voteListener.accept(vote);
        submitted = vote;

        statusLabel.setForeground(Theme.GOOD);
        statusLabel.setText(vote.isEmpty()
                ? "Ti sei astenuto. Puoi cambiare idea fino alla fine."
                : "Voto registrato per " + vote + ". Puoi cambiarlo fino alla fine.");

        for (VoteCard card : cards.values()) {
            card.repaint();
        }
    }

    public void update(List<PlayerInfo> players) {
        StringBuilder key = new StringBuilder();

        for (PlayerInfo player : players) {
            key.append(player.name()).append(player.connected()).append(player.expelled()).append(';');
        }

        if (!key.toString().equals(rosterKey)) {
            rosterKey = key.toString();
            grid.removeAll();
            cards.clear();

            for (PlayerInfo player : players) {
                VoteCard card = new VoteCard(player);
                cards.put(player.name(), card);
                grid.add(card);
            }

            grid.revalidate();
            grid.repaint();
        }

        int voted = 0;
        int connected = 0;

        for (PlayerInfo player : players) {
            VoteCard card = cards.get(player.name());

            if (card != null) {
                card.player = player;
            }

            if (player.name().equals(nickname)) {
                meExpelled = player.expelled();
            }

            if (player.connected() && !player.expelled()) {
                connected++;

                if (player.voted()) {
                    voted++;
                }
            }
        }

        progressLabel.setText(voted + "/" + connected + " HANNO VOTATO");

        if (meExpelled) {
            confirmButton.setEnabled(false);
            skipButton.setEnabled(false);
            statusLabel.setForeground(Theme.ERROR);
            statusLabel.setText("Sei stato espulso dalla rete: segui la votazione senza votare.");
        }

        if (selected != null && cards.containsKey(selected) && cards.get(selected).unavailable()) {
            selected = null;
            confirmButton.setEnabled(false);
        }
    }

    private class VoteCard extends JComponent {

        private PlayerInfo player;
        private boolean hovered;
        private double hoverAnim;
        private double selectAnim;
        private final boolean self;

        VoteCard(PlayerInfo player) {
            this.player = player;
            this.self = player.name().equals(nickname);

            setPreferredSize(new Dimension(160, 118));
            setCursor(Cursor.getPredefinedCursor(self ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));

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
                    if (self || unavailable() || meExpelled) {
                        return;
                    }

                    selected = VoteCard.this.player.name();
                    confirmButton.setEnabled(true);
                    statusLabel.setForeground(Theme.TEXT);
                    statusLabel.setText("Hai selezionato " + selected + ". Conferma il voto.");
                }
            });

            new Timer(30, e -> {
                boolean active = player.name().equals(selected);
                hoverAnim = Anim.approach(hoverAnim, hovered && !self ? 1 : 0, 0.2);
                selectAnim = Anim.approach(selectAnim, active ? 1 : 0, 0.18);
                repaint();
            }).start();
        }

        boolean unavailable() {
            return !player.connected() || player.expelled();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            double lift = hoverAnim * 3;
            Color color = Theme.playerColor(player.name());
            boolean disabled = unavailable();

            g2.translate(0, -lift);

            RoundRectangle2D shape = new RoundRectangle2D.Double(1, 4, w - 3, h - 8, 16, 16);

            g2.setColor(Theme.mix(Theme.PANEL_LIGHT, accent, selectAnim * 0.2 + hoverAnim * 0.06));
            g2.fill(shape);

            g2.setColor(Theme.alpha(selectAnim > 0.05 ? accent : Theme.BORDER, (int) (120 + selectAnim * 135)));
            g2.setStroke(new BasicStroke((float) (1.2 + selectAnim * 1.4)));
            g2.draw(shape);

            int avatar = 42;
            int ax = 16;
            int ay = 20;

            g2.setColor(Theme.alpha(color, disabled ? 25 : 55));
            g2.fill(new Ellipse2D.Double(ax, ay, avatar, avatar));
            g2.setColor(Theme.alpha(color, disabled ? 80 : 200));
            g2.draw(new Ellipse2D.Double(ax, ay, avatar, avatar));

            g2.setFont(Theme.font(18, Font.BOLD));
            FontMetrics fm = g2.getFontMetrics();
            String letter = player.name().substring(0, 1).toUpperCase();
            g2.drawString(letter, ax + (avatar - fm.stringWidth(letter)) / 2, ay + avatar / 2 + 7);

            g2.setFont(Theme.font(14, Font.BOLD));
            g2.setColor(disabled ? Theme.MUTED : Theme.TEXT);
            g2.drawString(clip(g2.getFontMetrics(), player.name(), w - ax - avatar - 26), ax + avatar + 12, ay + 18);

            g2.setFont(Theme.mono(11, Font.PLAIN));
            g2.setColor(Theme.MUTED);
            g2.drawString(player.workstation(), ax + avatar + 12, ay + 36);

            String tag = player.expelled() ? "ESPULSO" : disabled ? "DISCONNESSO" : self ? "TU"
                    : player.name().equals(submitted) ? "IL TUO VOTO" : null;

            if (tag != null) {
                g2.setFont(Theme.font(9, Font.BOLD));
                FontMetrics tm = g2.getFontMetrics();
                Color tagColor = disabled ? Theme.ERROR : player.name().equals(submitted) ? accent : Theme.STEEL;
                int tw = tm.stringWidth(tag) + 14;
                g2.setColor(Theme.alpha(tagColor, 40));
                g2.fillRoundRect(ax, h - 34, tw, 18, 18, 18);
                g2.setColor(tagColor);
                g2.drawRoundRect(ax, h - 34, tw, 18, 18, 18);
                g2.drawString(tag, ax + 7, h - 21);
            }

            if (player.voted()) {
                g2.setFont(Theme.font(10, Font.BOLD));
                FontMetrics vm = g2.getFontMetrics();
                String voted = "✓ HA VOTATO";
                g2.setColor(Theme.GOOD);
                g2.drawString(voted, w - vm.stringWidth(voted) - 14, h - 21);
            }

            g2.dispose();
        }

        private String clip(FontMetrics fm, String text, int width) {
            if (fm.stringWidth(text) <= width) {
                return text;
            }

            String cut = text;

            while (cut.length() > 1 && fm.stringWidth(cut + "...") > width) {
                cut = cut.substring(0, cut.length() - 1);
            }

            return cut + "...";
        }
    }
}
