package it.soc.client;

import it.soc.common.model.Evidence;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class EvidencePanel extends UiKit.RoundedPanel {

    private final ScrollableList list = new ScrollableList();
    private final JScrollPane scroll = UiKit.scroll(list);
    private final JLabel countLabel = UiKit.label("0 PROVE", 11, Font.BOLD, Theme.STEEL);
    private final JLabel emptyLabel;
    private final Map<String, EvidenceCard> cards = new HashMap<>();
    private final Set<String> shared = new HashSet<>();
    private final Function<String, String> systemName;
    private final Function<String, String> workstationOf;

    private Consumer<Evidence> shareListener;
    private boolean shareEnabled = true;

    public EvidencePanel(Function<String, String> systemName, Function<String, String> workstationOf) {
        super(18, Theme.PANEL);
        this.systemName = systemName;
        this.workstationOf = workstationOf;

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(18, 20, 16, 20));

        add(UiKit.cardHeader("Prove raccolte", "Risultati delle tue analisi dei log", countLabel),
                BorderLayout.NORTH);

        emptyLabel = UiKit.label(
                "<html><div style='text-align:center'>Nessuna prova raccolta.<br>"
                        + "Seleziona un sistema sulla mappa e analizza i log.</div></html>",
                12, Font.PLAIN, Theme.MUTED
        );
        emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
        emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        emptyLabel.setBorder(new EmptyBorder(40, 10, 40, 10));
        emptyLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
        list.add(emptyLabel);

        JPanel holder = new JPanel(new BorderLayout());
        holder.setBackground(Theme.PANEL_LIGHT);
        holder.setBorder(BorderFactory.createLineBorder(Theme.BORDER_SOFT));
        holder.add(scroll);

        list.setBorder(new EmptyBorder(8, 8, 8, 8));

        add(holder, BorderLayout.CENTER);
    }

    public void setShareListener(Consumer<Evidence> listener) {
        this.shareListener = listener;
    }

    public void setShareEnabled(boolean enabled) {
        this.shareEnabled = enabled;

        for (EvidenceCard card : cards.values()) {
            card.refreshButton();
        }
    }

    public void addEvidence(List<Evidence> entries) {
        if (entries.isEmpty()) {
            return;
        }

        list.remove(emptyLabel);

        for (int i = entries.size() - 1; i >= 0; i--) {
            Evidence evidence = entries.get(i);
            EvidenceCard existing = cards.get(evidence.id());

            if (existing != null) {
                existing.highlight();
                continue;
            }

            EvidenceCard card = new EvidenceCard(evidence);
            cards.put(evidence.id(), card);
            list.add(card, 0);
            list.add(Box.createVerticalStrut(8), 1);
        }

        countLabel.setText(cards.size() + (cards.size() == 1 ? " PROVA" : " PROVE"));
        list.revalidate();
        list.repaint();

        SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
    }

    public void markShared(String id) {
        shared.add(id);
        EvidenceCard card = cards.get(id);

        if (card != null) {
            card.refreshButton();
        }
    }

    public int count() {
        return cards.size();
    }

    private class EvidenceCard extends JPanel {

        private final Evidence evidence;
        private final JButton shareButton = new JButton("CONDIVIDI");
        private double flash = 1;
        private double reveal = 0;

        EvidenceCard(Evidence evidence) {
            this.evidence = evidence;

            setOpaque(false);
            setLayout(new BorderLayout(0, 6));
            setBorder(new EmptyBorder(10, 16, 10, 12));
            setAlignmentX(Component.LEFT_ALIGNMENT);

            Color severity = Theme.severity(evidence.severity());

            JPanel top = new JPanel(new BorderLayout(8, 0));
            top.setOpaque(false);

            JPanel tags = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            tags.setOpaque(false);
            tags.add(pill(severityName(evidence.severity()), severity));
            tags.add(UiKit.label(systemName.apply(evidence.system()), 11, Font.BOLD, Theme.TEXT));

            JLabel time = new JLabel(evidence.time() + "  " + evidence.id());
            time.setFont(Theme.mono(10, Font.PLAIN));
            time.setForeground(Theme.MUTED);

            top.add(tags, BorderLayout.WEST);
            top.add(time, BorderLayout.EAST);

            JTextArea text = ScrollableList.wrap(evidence.text(), 12, Font.PLAIN, Theme.TEXT);

            JPanel bottom = new JPanel(new BorderLayout());
            bottom.setOpaque(false);

            if (evidence.origin() != null) {
                String ws = workstationOf.apply(evidence.origin());
                JLabel origin = UiKit.label("ORIGINE  " + (ws == null ? "" : ws + " · ") + evidence.origin(),
                        10, Font.BOLD, Theme.playerColor(evidence.origin()));
                bottom.add(origin, BorderLayout.WEST);
            } else {
                bottom.add(UiKit.label("ORIGINE NON ATTRIBUITA", 10, Font.BOLD, Theme.alpha(Theme.MUTED, 170)),
                        BorderLayout.WEST);
            }

            UiKit.styleButton(shareButton, new Color(45, 60, 71), new Color(62, 82, 96), Theme.TEXT);
            shareButton.setFont(Theme.font(9, Font.BOLD));
            shareButton.setPreferredSize(new Dimension(94, 24));
            shareButton.setMargin(new Insets(0, 4, 0, 4));
            shareButton.addActionListener(e -> {
                if (shareListener != null && !shared.contains(evidence.id()) && shareEnabled) {
                    shareListener.accept(evidence);
                }
            });

            bottom.add(shareButton, BorderLayout.EAST);

            add(top, BorderLayout.NORTH);
            add(text, BorderLayout.CENTER);
            add(bottom, BorderLayout.SOUTH);

            refreshButton();

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    flash = Math.max(flash, 0.35);
                }
            });

            Timer timer = new Timer(30, null);
            timer.addActionListener(e -> {
                flash = Math.max(0, flash - 0.025);
                double previous = reveal;
                reveal = Anim.approach(reveal, 1, 0.18);

                if (previous != reveal) {
                    revalidate();
                }

                repaint();

                if (flash <= 0 && reveal >= 1) {
                    timer.stop();
                }
            });
            timer.start();
        }

        void highlight() {
            flash = 1;
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

        void refreshButton() {
            boolean isShared = shared.contains(evidence.id());
            shareButton.setText(isShared ? "CONDIVISA" : "CONDIVIDI");
            shareButton.setEnabled(!isShared && shareEnabled);
            shareButton.setForeground(isShared ? Theme.GOOD : Theme.TEXT);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            return new Dimension(size.width, (int) Math.max(1, size.height * reveal));
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color severity = Theme.severity(evidence.severity());
            RoundRectangle2D shape = new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);

            g2.setColor(Theme.mix(new Color(36, 49, 59), severity, 0.06 + flash * 0.22));
            g2.fill(shape);
            g2.setColor(Theme.alpha(severity, (int) (60 + flash * 160)));
            g2.draw(shape);

            g2.setColor(severity);
            g2.fillRoundRect(0, 0, 5, getHeight(), 5, 5);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static JLabel pill(String text, Color color) {
        JLabel label = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.alpha(color, 45));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
                g2.setColor(Theme.alpha(color, 170));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };

        label.setFont(Theme.font(9, Font.BOLD));
        label.setForeground(color);
        label.setBorder(new EmptyBorder(2, 8, 2, 8));
        return label;
    }

    public static String severityName(String severity) {
        if (severity == null) {
            return "INFO";
        }

        return switch (severity) {
            case "CRITICAL" -> "CRITICO";
            case "HIGH" -> "ALTO";
            case "MEDIUM" -> "MEDIO";
            case "LOW" -> "BASSO";
            default -> "INFO";
        };
    }
}
