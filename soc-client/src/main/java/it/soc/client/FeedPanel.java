package it.soc.client;

import it.soc.common.model.ChatMessage;
import it.soc.common.model.Evidence;
import it.soc.common.model.GameEvent;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;
import java.util.function.Function;

public class FeedPanel extends UiKit.RoundedPanel {

    private static final int MAX_ITEMS = 160;

    private final ScrollableList list = new ScrollableList();
    private final JScrollPane scroll = UiKit.scroll(list);
    private final JTextField input = new JTextField();
    private final JButton sendButton = new JButton("INVIA");
    private final String nickname;
    private final Color accent;
    private final Function<String, String> systemName;
    private final Function<String, String> workstationOf;
    private final JLabel liveLabel = UiKit.label("● LIVE", 10, Font.BOLD, Theme.GOOD);

    private Consumer<String> sendListener;

    public FeedPanel(String nickname, Color accent,
                     Function<String, String> systemName, Function<String, String> workstationOf) {
        super(18, Theme.PANEL);
        this.nickname = nickname;
        this.accent = accent;
        this.systemName = systemName;
        this.workstationOf = workstationOf;

        setLayout(new BorderLayout(0, 10));
        setBorder(new EmptyBorder(18, 20, 16, 20));

        add(UiKit.cardHeader("Canale operativo", "Chat del team, eventi e prove condivise", liveLabel),
                BorderLayout.NORTH);

        new Timer(800, e -> liveLabel.setForeground(
                liveLabel.getForeground().equals(Theme.GOOD) ? new Color(80, 110, 102) : Theme.GOOD
        )).start();

        list.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel holder = new JPanel(new BorderLayout());
        holder.setBackground(Theme.PANEL_LIGHT);
        holder.setBorder(BorderFactory.createLineBorder(Theme.BORDER_SOFT));
        holder.add(scroll);

        add(holder, BorderLayout.CENTER);
        add(createInput(), BorderLayout.SOUTH);
    }

    private JPanel createInput() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);

        input.setFont(Theme.font(13, Font.PLAIN));
        input.setForeground(Theme.TEXT);
        input.setCaretColor(Theme.TEXT);
        input.setBackground(new Color(35, 45, 53));
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(75, 91, 103)),
                new EmptyBorder(9, 11, 9, 11)
        ));

        UiKit.styleButton(sendButton, accent, Theme.mix(accent, Color.WHITE, 0.2), new Color(20, 29, 36));
        sendButton.setPreferredSize(new Dimension(78, 38));

        input.addActionListener(e -> send());
        sendButton.addActionListener(e -> send());

        panel.add(input, BorderLayout.CENTER);
        panel.add(sendButton, BorderLayout.EAST);

        return panel;
    }

    public void setSendListener(Consumer<String> listener) {
        this.sendListener = listener;
    }

    public void setInputEnabled(boolean enabled) {
        input.setEnabled(enabled);
        sendButton.setEnabled(enabled);
    }

    private void send() {
        String text = input.getText().strip();

        if (text.isEmpty() || sendListener == null) {
            return;
        }

        sendListener.accept(text);
        input.setText("");
    }

    public void addChat(ChatMessage message) {
        append(new ChatItem(message));
    }

    public void addEvent(GameEvent event) {
        append(new EventItem(event.title(), event.description(), Theme.severity(event.severity())));
    }

    public void addNotice(String title, String description, Color color) {
        append(new EventItem(title, description, color));
    }

    private void append(JComponent item) {
        JScrollBar bar = scroll.getVerticalScrollBar();
        boolean atBottom = bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 40;

        item.setAlignmentX(Component.LEFT_ALIGNMENT);
        list.add(item);
        list.add(Box.createVerticalStrut(7));

        while (list.getComponentCount() > MAX_ITEMS * 2) {
            list.remove(0);
            list.remove(0);
        }

        list.revalidate();
        list.repaint();

        if (atBottom) {
            SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(
                    () -> bar.setValue(bar.getMaximum())
            ));
        }
    }

    private abstract static class FadeItem extends JPanel {

        protected double flash = 1;

        FadeItem() {
            setOpaque(false);

            Timer timer = new Timer(30, null);
            timer.addActionListener(e -> {
                flash = Math.max(0, flash - 0.03);
                repaint();

                if (flash <= 0) {
                    timer.stop();
                }
            });
            timer.start();
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    private class ChatItem extends FadeItem {

        private final Color color;
        private final boolean own;

        ChatItem(ChatMessage message) {
            own = message.from().equals(nickname);
            color = Theme.playerColor(message.from());

            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(8, 10, 8, 10));

            JLabel avatar = new JLabel(message.from().substring(0, 1).toUpperCase(), SwingConstants.CENTER) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Theme.alpha(color, 50));
                    g2.fill(new Ellipse2D.Double(0, 0, 29, 29));
                    g2.setColor(Theme.alpha(color, 160));
                    g2.draw(new Ellipse2D.Double(0, 0, 28, 28));
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            avatar.setFont(Theme.font(12, Font.BOLD));
            avatar.setForeground(color);
            avatar.setPreferredSize(new Dimension(30, 30));
            avatar.setVerticalAlignment(SwingConstants.CENTER);

            JPanel avatarHolder = new JPanel(new BorderLayout());
            avatarHolder.setOpaque(false);
            avatarHolder.add(avatar, BorderLayout.NORTH);

            JPanel body = new JPanel();
            body.setOpaque(false);
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

            JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            head.setOpaque(false);
            head.setAlignmentX(Component.LEFT_ALIGNMENT);

            String ws = workstationOf.apply(message.from());
            JLabel name = UiKit.label((own ? "Tu" : message.from()) + "  ", 12, Font.BOLD, color);
            JLabel meta = new JLabel((ws == null ? "" : ws + "  ") + message.time());
            meta.setFont(Theme.mono(10, Font.PLAIN));
            meta.setForeground(Theme.MUTED);

            head.add(name);
            head.add(meta);

            body.add(head);
            body.add(Box.createVerticalStrut(4));

            if (message.evidence() == null) {
                body.add(ScrollableList.wrap(message.text(), 12, Font.PLAIN, Theme.TEXT));
            } else {
                body.add(ScrollableList.wrap(message.text(), 11, Font.ITALIC, Theme.MUTED));
                body.add(Box.createVerticalStrut(6));
                body.add(new SharedEvidence(message.evidence()));
            }

            add(avatarHolder, BorderLayout.WEST);
            add(body, BorderLayout.CENTER);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color base = own ? Theme.mix(new Color(34, 47, 57), accent, 0.08) : new Color(34, 47, 57);
            g2.setColor(Theme.mix(base, color, flash * 0.18));
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 12, 12));

            if (own) {
                g2.setColor(Theme.alpha(accent, 90));
                g2.draw(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 12, 12));
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }

    private class SharedEvidence extends JPanel {

        private final Color severity;

        SharedEvidence(Evidence evidence) {
            severity = Theme.severity(evidence.severity());

            setOpaque(false);
            setLayout(new BorderLayout(0, 5));
            setBorder(new EmptyBorder(8, 14, 8, 10));
            setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            top.setOpaque(false);
            top.add(EvidencePanel.pill("VERIFICATA", Theme.GOOD));
            top.add(EvidencePanel.pill(EvidencePanel.severityName(evidence.severity()), severity));

            JLabel where = new JLabel(systemName.apply(evidence.system()) + "  " + evidence.time());
            where.setFont(Theme.mono(10, Font.BOLD));
            where.setForeground(Theme.MUTED);
            top.add(where);

            add(top, BorderLayout.NORTH);
            add(ScrollableList.wrap(evidence.text(), 12, Font.PLAIN, Theme.TEXT), BorderLayout.CENTER);

            if (evidence.origin() != null) {
                String ws = workstationOf.apply(evidence.origin());
                add(UiKit.label("ORIGINE  " + (ws == null ? "" : ws + " · ") + evidence.origin(),
                        10, Font.BOLD, Theme.playerColor(evidence.origin())), BorderLayout.SOUTH);
            }
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(24, 33, 41));
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 10, 10));
            g2.setColor(Theme.alpha(severity, 120));
            g2.draw(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 10, 10));
            g2.setColor(severity);
            g2.fillRoundRect(0, 0, 4, getHeight(), 4, 4);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class EventItem extends FadeItem {

        private final Color color;

        EventItem(String title, String description, Color color) {
            this.color = color;

            setLayout(new BorderLayout(0, 3));
            setBorder(new EmptyBorder(8, 16, 8, 10));

            add(UiKit.label(title.toUpperCase(), 10, Font.BOLD, color), BorderLayout.NORTH);

            if (description != null && !description.isBlank()) {
                add(ScrollableList.wrap(description, 11, Font.PLAIN, Theme.mix(Theme.TEXT, Theme.MUTED, 0.3)),
                        BorderLayout.CENTER);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.alpha(color, (int) (18 + flash * 50)));
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 10, 10));
            g2.setColor(color);
            g2.fill(new Ellipse2D.Double(5, 12, 5, 5));
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
