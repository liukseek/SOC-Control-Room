package it.soc.server.admin;

import it.soc.common.model.Phase;
import it.soc.common.model.Role;
import it.soc.server.ConnectionManager;
import it.soc.server.GameConfig;
import it.soc.server.ServerLog;
import it.soc.server.game.AdminSnapshot;
import it.soc.server.game.GameManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.net.InetAddress;
import java.util.List;

public class AdminWindow extends JFrame {

    private static final Color BACKGROUND = new Color(13, 20, 26);
    private static final Color PANEL = new Color(24, 33, 41);
    private static final Color PANEL_LIGHT = new Color(31, 43, 52);
    private static final Color STEEL = new Color(112, 142, 162);
    private static final Color TEXT = new Color(237, 242, 245);
    private static final Color MUTED = new Color(155, 170, 181);
    private static final Color BORDER = new Color(65, 80, 91);
    private static final Color GOOD = new Color(137, 181, 169);
    private static final Color WARN = new Color(214, 178, 110);
    private static final Color DANGER = new Color(220, 120, 105);

    private final GameManager gameManager;
    private final ConnectionManager connectionManager;
    private final int port;

    private final JLabel phaseLabel = new JLabel("LOBBY");
    private final JLabel timerLabel = new JLabel("--:--");
    private final JLabel clientsLabel = new JLabel("0");
    private final JLabel hostLabel = new JLabel("-");
    private final Meter exfiltrationMeter = new Meter();
    private final JPanel playersList = new JPanel();
    private final JTextPane console = new JTextPane();
    private final JCheckBox showRoles = new JCheckBox("Mostra ruoli");
    private final JButton startButton = new JButton("AVVIA PARTITA");
    private final JButton stopButton = new JButton("TERMINA PARTITA");

    private String lastPlayersKey = "";

    private AdminWindow(GameManager gameManager, ConnectionManager connectionManager, int port) {
        this.gameManager = gameManager;
        this.connectionManager = connectionManager;
        this.port = port;

        setTitle("SOC: CONTROL ROOM - Pannello amministratore");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1180, 760);
        setMinimumSize(new Dimension(980, 640));
        setLocationRelativeTo(null);

        Backdrop backdrop = new Backdrop();
        backdrop.setLayout(new BorderLayout(22, 22));
        backdrop.setBorder(new EmptyBorder(30, 38, 30, 38));
        setContentPane(backdrop);

        backdrop.add(createHeader(), BorderLayout.NORTH);
        backdrop.add(createBody(), BorderLayout.CENTER);

        ServerLog.addListener((level, line) ->
                SwingUtilities.invokeLater(() -> appendLog(level, line))
        );

        new Timer(400, e -> refresh()).start();
        refresh();
    }

    public static void open(GameManager gameManager, ConnectionManager connectionManager, int port) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception e) {
                System.err.println("Look and Feel non disponibile.");
            }

            new AdminWindow(gameManager, connectionManager, port).setVisible(true);
        });
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));

        JLabel small = label("SOC: CONTROL ROOM", 13, Font.BOLD, STEEL);
        JLabel title = label("Pannello amministratore", 32, Font.BOLD, TEXT);
        JLabel subtitle = label("Gestione del server e delle sessioni di gioco", 14, Font.PLAIN, MUTED);

        brand.add(small);
        brand.add(Box.createVerticalStrut(10));
        brand.add(title);
        brand.add(Box.createVerticalStrut(5));
        brand.add(subtitle);

        JPanel badge = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 7));
        badge.setBackground(new Color(38, 57, 64));
        badge.setBorder(BorderFactory.createLineBorder(new Color(75, 110, 119)));

        JLabel dot = label("●", 10, Font.BOLD, GOOD);
        JLabel text = label("SERVER ATTIVO · " + localAddress() + ":" + port, 10, Font.BOLD,
                new Color(181, 207, 201));

        badge.add(dot);
        badge.add(text);

        new Timer(700, e -> dot.setForeground(
                dot.getForeground().equals(GOOD) ? new Color(70, 100, 95) : GOOD
        )).start();

        JPanel right = new JPanel(new GridBagLayout());
        right.setOpaque(false);
        right.add(badge);

        header.add(brand, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);

        return header;
    }

    private JPanel createBody() {
        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1;

        gbc.gridx = 0;
        gbc.weightx = 0.3;
        gbc.insets = new Insets(0, 0, 0, 12);
        body.add(createSessionCard(), gbc);

        JPanel right = new JPanel(new GridLayout(2, 1, 0, 18));
        right.setOpaque(false);
        right.add(createPlayersCard());
        right.add(createConsoleCard());

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        gbc.insets = new Insets(0, 12, 0, 0);
        body.add(right, gbc);

        return body;
    }

    private JPanel createSessionCard() {
        Card card = new Card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(24, 24, 24, 24));

        card.add(left(label("Sessione", 21, Font.BOLD, TEXT)));
        card.add(Box.createVerticalStrut(4));
        card.add(left(label("Stato della partita in corso", 12, Font.PLAIN, MUTED)));
        card.add(Box.createVerticalStrut(22));

        JPanel stats = new JPanel(new GridLayout(2, 2, 10, 10));
        stats.setOpaque(false);
        stats.setAlignmentX(Component.LEFT_ALIGNMENT);
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        phaseLabel.setFont(new Font("Arial", Font.BOLD, 16));
        timerLabel.setFont(new Font("Monospaced", Font.BOLD, 22));
        clientsLabel.setFont(new Font("Arial", Font.BOLD, 22));
        hostLabel.setFont(new Font("Arial", Font.BOLD, 14));

        stats.add(stat("FASE", phaseLabel));
        stats.add(stat("TEMPO", timerLabel));
        stats.add(stat("CLIENT", clientsLabel));
        stats.add(stat("HOST", hostLabel));

        card.add(stats);
        card.add(Box.createVerticalStrut(20));
        card.add(left(label("DATI ESFILTRATI", 10, Font.BOLD, MUTED)));
        card.add(Box.createVerticalStrut(8));

        exfiltrationMeter.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(exfiltrationMeter);

        card.add(Box.createVerticalStrut(22));
        card.add(left(label("CONFIGURAZIONE", 10, Font.BOLD, MUTED)));
        card.add(Box.createVerticalStrut(10));

        GameConfig config = gameManager.config();

        card.add(configRow("Giocatori minimi", config.minPlayers(), 2, 12, 1, config::setMinPlayers));
        card.add(Box.createVerticalStrut(8));
        card.add(configRow("Indagine (s)", config.investigationSeconds(), 30, 900, 30,
                config::setInvestigationSeconds));
        card.add(Box.createVerticalStrut(8));
        card.add(configRow("Voto (s)", config.votingSeconds(), 15, 300, 15, config::setVotingSeconds));

        card.add(Box.createVerticalGlue());
        card.add(Box.createVerticalStrut(18));

        styleButton(startButton, STEEL, new Color(145, 169, 185), new Color(20, 29, 36));
        styleButton(stopButton, new Color(125, 75, 66), new Color(153, 88, 76), Color.WHITE);

        startButton.addActionListener(e -> {
            String error = gameManager.startGame(null);

            if (error != null) {
                ServerLog.warn(error);
            }
        });

        stopButton.addActionListener(e -> gameManager.stopGame());

        card.add(startButton);
        card.add(Box.createVerticalStrut(10));
        card.add(stopButton);

        return card;
    }

    private JPanel configRow(String text, int value, int min, int max, int step,
                             java.util.function.IntConsumer setter) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        spinner.setPreferredSize(new Dimension(80, 30));
        spinner.setBorder(BorderFactory.createLineBorder(BORDER));

        JComponent editor = spinner.getEditor();

        if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
            JTextField field = defaultEditor.getTextField();
            field.setBackground(PANEL_LIGHT);
            field.setForeground(TEXT);
            field.setCaretColor(TEXT);
            field.setFont(new Font("Monospaced", Font.BOLD, 13));
            field.setBorder(new EmptyBorder(0, 8, 0, 4));
        }

        spinner.addChangeListener(e -> setter.accept((Integer) spinner.getValue()));

        row.add(label(text, 12, Font.PLAIN, TEXT), BorderLayout.WEST);
        row.add(spinner, BorderLayout.EAST);

        return row;
    }

    private JPanel stat(String title, JLabel value) {
        JPanel panel = new JPanel();
        panel.setBackground(PANEL_LIGHT);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(12, 14, 12, 14));

        value.setForeground(TEXT);

        panel.add(label(title, 10, Font.BOLD, MUTED));
        panel.add(Box.createVerticalStrut(6));
        panel.add(value);

        return panel;
    }

    private JPanel createPlayersCard() {
        Card card = new Card();
        card.setLayout(new BorderLayout(10, 14));
        card.setBorder(new EmptyBorder(20, 22, 18, 22));

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(label("Operatori", 19, Font.BOLD, TEXT));
        titles.add(Box.createVerticalStrut(4));
        titles.add(label("Giocatori in lobby o in partita", 12, Font.PLAIN, MUTED));

        showRoles.setOpaque(false);
        showRoles.setForeground(MUTED);
        showRoles.setFont(new Font("Arial", Font.BOLD, 11));
        showRoles.setFocusPainted(false);
        showRoles.addActionListener(e -> lastPlayersKey = "");

        heading.add(titles, BorderLayout.WEST);
        heading.add(showRoles, BorderLayout.EAST);

        playersList.setOpaque(false);
        playersList.setLayout(new BoxLayout(playersList, BoxLayout.Y_AXIS));

        JScrollPane scroll = new JScrollPane(playersList);
        slim(scroll);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(51, 66, 77)));
        scroll.getViewport().setBackground(PANEL_LIGHT);

        JPanel holder = new JPanel(new BorderLayout());
        holder.setBackground(PANEL_LIGHT);
        holder.add(scroll);

        card.add(heading, BorderLayout.NORTH);
        card.add(holder, BorderLayout.CENTER);

        return card;
    }

    private JPanel createConsoleCard() {
        Card card = new Card();
        card.setLayout(new BorderLayout(10, 12));
        card.setBorder(new EmptyBorder(20, 22, 18, 22));

        card.add(label("Registro del server", 19, Font.BOLD, TEXT), BorderLayout.NORTH);

        console.setEditable(false);
        console.setBackground(new Color(17, 25, 31));
        console.setForeground(MUTED);
        console.setFont(new Font("Monospaced", Font.PLAIN, 12));
        console.setBorder(new EmptyBorder(10, 12, 10, 12));

        JScrollPane scroll = new JScrollPane(console);
        slim(scroll);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(51, 66, 77)));

        card.add(scroll, BorderLayout.CENTER);

        return card;
    }

    private void appendLog(String level, String line) {
        StyledDocument document = console.getStyledDocument();
        SimpleAttributeSet style = new SimpleAttributeSet();

        StyleConstants.setForeground(style, switch (level) {
            case ServerLog.GAME -> GOOD;
            case ServerLog.WARN -> WARN;
            case ServerLog.ERROR -> DANGER;
            default -> MUTED;
        });

        try {
            document.insertString(document.getLength(), line + "\n", style);

            if (document.getLength() > 60000) {
                document.remove(0, 20000);
            }
        } catch (BadLocationException e) {
            System.err.println("Errore registro: " + e.getMessage());
        }

        console.setCaretPosition(document.getLength());
    }

    private void refresh() {
        AdminSnapshot snapshot = gameManager.snapshot();

        boolean inGame = snapshot.phase() != Phase.LOBBY;

        phaseLabel.setText(phaseName(snapshot.phase()));
        phaseLabel.setForeground(inGame ? GOOD : STEEL);
        timerLabel.setText(inGame
                ? String.format("%02d:%02d", snapshot.secondsLeft() / 60, snapshot.secondsLeft() % 60)
                : "--:--");
        clientsLabel.setText(String.valueOf(connectionManager.getClientCount()));
        hostLabel.setText(snapshot.host() == null ? "-" : snapshot.host());
        exfiltrationMeter.setValue(snapshot.exfiltration());

        startButton.setEnabled(!inGame);
        stopButton.setEnabled(inGame);

        StringBuilder key = new StringBuilder(String.valueOf(inGame));

        if (inGame) {
            for (AdminSnapshot.Player p : snapshot.players()) {
                key.append(p).append(';');
            }
        } else {
            key.append(snapshot.lobby()).append(snapshot.host());
        }

        if (!key.toString().equals(lastPlayersKey)) {
            lastPlayersKey = key.toString();
            rebuildPlayers(snapshot);
        }
    }

    private void rebuildPlayers(AdminSnapshot snapshot) {
        playersList.removeAll();

        if (snapshot.phase() == Phase.LOBBY) {
            List<String> lobby = snapshot.lobby();

            if (lobby.isEmpty()) {
                playersList.add(row("Nessun operatore connesso", "In attesa di connessioni", null, MUTED));
            }

            for (String name : lobby) {
                boolean host = name.equals(snapshot.host());
                playersList.add(row(name, host ? "HOST · IN LOBBY" : "IN LOBBY", null, host ? STEEL : MUTED));
            }
        } else {
            for (AdminSnapshot.Player p : snapshot.players()) {
                String role = showRoles.isSelected()
                        ? (p.role() == Role.INSIDER ? "INSIDER" : "ANALISTA")
                        : "RUOLO NASCOSTO";

                String details = p.workstation() + " · " + role + " · " + p.actions() + " azioni · "
                        + p.evidence() + " prove" + (p.voted() ? " · HA VOTATO" : "")
                        + (p.connected() ? "" : " · DISCONNESSO");

                Color accent = !p.connected() ? DANGER
                        : showRoles.isSelected() && p.role() == Role.INSIDER ? DANGER : STEEL;

                playersList.add(row(p.name(), details, p.workstation(), accent));
            }
        }

        playersList.revalidate();
        playersList.repaint();
    }

    private JPanel row(String name, String details, String tag, Color accent) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setBackground(PANEL_LIGHT);
        row.setBorder(new EmptyBorder(9, 12, 9, 12));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel avatar = new JLabel(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(),
                SwingConstants.CENTER);
        avatar.setOpaque(true);
        avatar.setBackground(new Color(49, 67, 80));
        avatar.setForeground(accent);
        avatar.setFont(new Font("Arial", Font.BOLD, 14));
        avatar.setPreferredSize(new Dimension(34, 34));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(label(name, 13, Font.BOLD, TEXT));
        text.add(Box.createVerticalStrut(3));
        text.add(label(details, 10, Font.PLAIN, accent == DANGER ? DANGER : MUTED));

        row.add(avatar, BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);

        if (tag != null) {
            row.add(label(tag, 11, Font.BOLD, STEEL), BorderLayout.EAST);
        }

        return row;
    }

    private static String phaseName(Phase phase) {
        return switch (phase) {
            case LOBBY -> "LOBBY";
            case BRIEFING -> "BRIEFING";
            case INVESTIGATION -> "INDAGINE";
            case VOTING -> "VOTAZIONE";
            case RESULTS -> "RISULTATI";
        };
    }

    private static String localAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "localhost";
        }
    }

    private static JLabel label(String text, int size, int style, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Arial", style, size));
        label.setForeground(color);
        return label;
    }

    private static JComponent left(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        return component;
    }

    private static void styleButton(JButton button, Color normal, Color hover, Color foreground) {
        button.setFont(new Font("Arial", Font.BOLD, 12));
        button.setForeground(foreground);
        button.setBackground(normal);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        button.setPreferredSize(new Dimension(240, 44));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(hover);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(normal);
            }
        });
    }

    private static void slim(JScrollPane scroll) {
        JScrollBar bar = scroll.getVerticalScrollBar();
        bar.setPreferredSize(new Dimension(8, 0));
        bar.setUnitIncrement(16);
        bar.setOpaque(false);
        bar.setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                thumbColor = new Color(70, 88, 101);
                trackColor = new Color(0, 0, 0, 0);
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return empty();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return empty();
            }

            private JButton empty() {
                JButton button = new JButton();
                button.setPreferredSize(new Dimension(0, 0));
                return button;
            }

            @Override
            protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) {
            }

            @Override
            protected void paintThumb(Graphics g, JComponent c, Rectangle bounds) {
                if (bounds.isEmpty()) {
                    return;
                }

                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(thumbColor);
                g2.fillRoundRect(bounds.x + 2, bounds.y + 2, bounds.width - 4, bounds.height - 4, 6, 6);
                g2.dispose();
            }
        });
    }

    private static class Card extends JPanel {

        Card() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(PANEL);
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 18, 18));
            g2.setColor(BORDER);
            g2.draw(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 18, 18));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class Meter extends JComponent {

        private double shown;
        private int target;

        Meter() {
            setPreferredSize(new Dimension(240, 26));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

            new Timer(30, e -> {
                if (Math.abs(shown - target) > 0.1) {
                    shown += (target - shown) * 0.12;
                    repaint();
                }
            }).start();
        }

        void setValue(int value) {
            target = value;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = 10;
            int y = 4;

            g2.setColor(new Color(40, 52, 61));
            g2.fillRoundRect(0, y, w - 46, h, h, h);

            Color fill = shown < 50 ? WARN : DANGER;
            g2.setColor(fill);
            g2.fillRoundRect(0, y, (int) ((w - 46) * shown / 100.0), h, h, h);

            g2.setColor(TEXT);
            g2.setFont(new Font("Monospaced", Font.BOLD, 13));
            g2.drawString(Math.round(shown) + "%", w - 40, y + h);

            g2.dispose();
        }
    }

    private static class Backdrop extends JPanel {

        private double phase;

        Backdrop() {
            setOpaque(true);
            setBackground(BACKGROUND);

            new Timer(40, e -> {
                phase = (phase + 0.015) % (Math.PI * 2);
                repaint();
            }).start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            g2.setPaint(new GradientPaint(0, 0, new Color(27, 40, 49), w, h, BACKGROUND));
            g2.fillRect(0, 0, w, h);

            g2.setColor(new Color(130, 160, 180, 10));

            for (int x = 0; x < w; x += 50) {
                g2.drawLine(x, 0, x, h);
            }

            for (int y = 0; y < h; y += 50) {
                g2.drawLine(0, y, w, y);
            }

            int alpha = 8 + (int) ((0.5 + 0.5 * Math.sin(phase)) * 8);
            g2.setColor(new Color(95, 137, 160, alpha));
            g2.fill(new Ellipse2D.Double(w * 0.6, h * 0.05, w * 0.4, h * 0.5));

            int y = (int) (phase / (Math.PI * 2) * h);
            g2.setPaint(new GradientPaint(0, y - 12, new Color(120, 160, 185, 0), 0, y,
                    new Color(120, 160, 185, 18)));
            g2.fillRect(0, y - 12, w, 12);

            g2.dispose();
        }
    }
}
