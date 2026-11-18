
package it.soc.client;

import it.soc.common.Message;
import it.soc.common.MessageType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;

public class LobbyWindow extends JFrame {

    private static final Color BACKGROUND = new Color(13, 20, 26);
    private static final Color PANEL = new Color(24, 33, 41);
    private static final Color PANEL_LIGHT = new Color(31, 43, 52);
    private static final Color STEEL = new Color(112, 142, 162);
    private static final Color TEXT = new Color(237, 242, 245);
    private static final Color MUTED = new Color(155, 170, 181);
    private static final Color BORDER = new Color(65, 80, 91);
    private static final Color ERROR = new Color(220, 145, 125);

    private final DefaultListModel<String> playerModel =
            new DefaultListModel<>();

    private final JLabel playerCountLabel = new JLabel("0 GIOCATORI");
    private final JLabel statusLabel =
            new JLabel("In attesa di giocatori");

    private final JLabel connectionLabel =
            new JLabel("SERVER COLLEGATO");

    private final JLabel connectionDot = new JLabel("●");
    private final JLabel bigCountLabel = new JLabel("0");

    private final JButton leaveButton =
            new JButton("ESCI DALLA SESSIONE");

    private final NetworkClient networkClient;
    private final String nickname;

    private AnimatedBackground animatedBackground;
    private Timer countAnimationTimer;

    public LobbyWindow(NetworkClient networkClient, String nickname) {
        this.networkClient = networkClient;
        this.nickname = nickname;

        setTitle("SOC: CONTROL ROOM - Sala operativa");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1250, 780);
        setMinimumSize(new Dimension(950, 650));
        setLocationRelativeTo(null);

        animatedBackground = new AnimatedBackground();
        setContentPane(animatedBackground);

        animatedBackground.setLayout(new BorderLayout(25, 25));
        animatedBackground.setBorder(
                new EmptyBorder(35, 45, 30, 45)
        );

        animatedBackground.add(createHeader(), BorderLayout.NORTH);
        animatedBackground.add(createMainContent(), BorderLayout.CENTER);
        animatedBackground.add(createFooter(), BorderLayout.SOUTH);

        leaveButton.addActionListener(e -> leaveLobby());

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                leaveLobby();
            }
        });

        networkClient.setMessageListener(message ->
                SwingUtilities.invokeLater(
                        () -> handleServerMessage(message)
                )
        );

        networkClient.setDisconnectListener(reason ->
                SwingUtilities.invokeLater(this::showDisconnected)
        );
    }

    // --------------------------------------------------
    // HEADER
    // --------------------------------------------------

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel brandPanel = new JPanel();
        brandPanel.setOpaque(false);
        brandPanel.setLayout(new BoxLayout(
                brandPanel, BoxLayout.Y_AXIS
        ));

        JLabel brand = new JLabel("SOC: CONTROL ROOM");
        brand.setFont(new Font("Arial", Font.BOLD, 13));
        brand.setForeground(STEEL);

        JLabel title = new JLabel("Sala operativa");
        title.setFont(new Font("Arial", Font.BOLD, 36));
        title.setForeground(TEXT);

        JLabel subtitle = new JLabel(
                "Preparazione della sessione multiplayer"
        );
        subtitle.setFont(new Font("Arial", Font.PLAIN, 14));
        subtitle.setForeground(MUTED);

        brandPanel.add(brand);
        brandPanel.add(Box.createVerticalStrut(12));
        brandPanel.add(title);
        brandPanel.add(Box.createVerticalStrut(5));
        brandPanel.add(subtitle);

        JPanel rightPanel = new JPanel();
        rightPanel.setOpaque(false);
        rightPanel.setLayout(new BoxLayout(
                rightPanel, BoxLayout.Y_AXIS
        ));

        JLabel connectedAs = new JLabel("OPERATORE CONNESSO");
        connectedAs.setFont(new Font("Arial", Font.BOLD, 10));
        connectedAs.setForeground(MUTED);
        connectedAs.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel username = new JLabel(nickname);
        username.setFont(new Font("Arial", Font.BOLD, 19));
        username.setForeground(TEXT);
        username.setHorizontalAlignment(SwingConstants.CENTER);
        username.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel connectionBadge = createConnectionBadge();
        connectionBadge.setAlignmentX(Component.CENTER_ALIGNMENT);

        rightPanel.add(connectedAs);
        rightPanel.add(Box.createVerticalStrut(7));
        rightPanel.add(username);
        rightPanel.add(Box.createVerticalStrut(14));
        rightPanel.add(connectionBadge);

        header.add(brandPanel, BorderLayout.WEST);
        header.add(rightPanel, BorderLayout.EAST);

        return header;
    }

    private JPanel createConnectionBadge() {
        JPanel badge = new JPanel(new FlowLayout(
                FlowLayout.CENTER, 8, 6
        ));

        badge.setBackground(new Color(38, 57, 64));
        badge.setBorder(BorderFactory.createLineBorder(
                new Color(75, 110, 119)
        ));

        connectionDot.setFont(new Font("Arial", Font.BOLD, 10));
        connectionDot.setForeground(new Color(137, 181, 169));

        connectionLabel.setFont(new Font("Arial", Font.BOLD, 10));
        connectionLabel.setForeground(new Color(181, 207, 201));

        badge.add(connectionDot);
        badge.add(connectionLabel);

        return badge;
    }

    // --------------------------------------------------
    // MAIN CONTENT
    // --------------------------------------------------

    private JPanel createMainContent() {
        JPanel main = new JPanel(new GridBagLayout());
        main.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1;

        gbc.gridx = 0;
        gbc.weightx = 0.68;
        gbc.insets = new Insets(0, 0, 0, 14);
        main.add(createPlayersPanel(), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.32;
        gbc.insets = new Insets(0, 14, 0, 0);
        main.add(createSessionPanel(), gbc);

        return main;
    }

    // --------------------------------------------------
    // PLAYERS PANEL
    // --------------------------------------------------

    private JPanel createPlayersPanel() {
        RoundedPanel panel = new RoundedPanel(18, PANEL);
        panel.setLayout(new BorderLayout(18, 18));
        panel.setBorder(new EmptyBorder(25, 27, 25, 27));

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);

        JPanel titlePanel = new JPanel();
        titlePanel.setOpaque(false);
        titlePanel.setLayout(new BoxLayout(
                titlePanel, BoxLayout.Y_AXIS
        ));

        JLabel title = new JLabel("Operatori presenti");
        title.setFont(new Font("Arial", Font.BOLD, 21));
        title.setForeground(TEXT);

        JLabel subtitle = new JLabel(
                "Elenco dei partecipanti collegati alla sessione"
        );
        subtitle.setFont(new Font("Arial", Font.PLAIN, 12));
        subtitle.setForeground(MUTED);

        titlePanel.add(title);
        titlePanel.add(Box.createVerticalStrut(6));
        titlePanel.add(subtitle);

        playerCountLabel.setFont(new Font("Arial", Font.BOLD, 11));
        playerCountLabel.setForeground(STEEL);
        playerCountLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        heading.add(titlePanel, BorderLayout.WEST);
        heading.add(playerCountLabel, BorderLayout.EAST);

        JList<String> playerList = new JList<>(playerModel);
        playerList.setOpaque(false);
        playerList.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION
        );
        playerList.setCellRenderer(new PlayerRenderer());
        playerList.setFixedCellHeight(66);
        playerList.setBorder(new EmptyBorder(5, 3, 5, 3));

        JScrollPane scrollPane = new JScrollPane(playerList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel listContainer = new JPanel(new BorderLayout());
        listContainer.setBackground(PANEL_LIGHT);
        listContainer.setBorder(BorderFactory.createLineBorder(
                new Color(51, 66, 77)
        ));
        listContainer.add(scrollPane, BorderLayout.CENTER);

        JPanel bottomInfo = new JPanel(new BorderLayout());
        bottomInfo.setOpaque(false);

        JLabel bottomText = new JLabel(
                "I partecipanti vengono aggiornati automaticamente"
        );
        bottomText.setFont(new Font("Arial", Font.PLAIN, 11));
        bottomText.setForeground(MUTED);

        bottomInfo.add(bottomText, BorderLayout.WEST);

        panel.add(heading, BorderLayout.NORTH);
        panel.add(listContainer, BorderLayout.CENTER);
        panel.add(bottomInfo, BorderLayout.SOUTH);

        return panel;
    }

    // --------------------------------------------------
    // SESSION PANEL
    // --------------------------------------------------

    private JPanel createSessionPanel() {
        RoundedPanel panel = new RoundedPanel(18, PANEL);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(25, 24, 25, 24));

        JLabel heading = new JLabel("Sessione");
        heading.setFont(new Font("Arial", Font.BOLD, 21));
        heading.setForeground(TEXT);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel headingDescription = new JLabel(
                "Informazioni sulla partita"
        );
        headingDescription.setFont(new Font("Arial", Font.PLAIN, 12));
        headingDescription.setForeground(MUTED);
        headingDescription.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel countCard = new JPanel();
        countCard.setBackground(PANEL_LIGHT);
        countCard.setBorder(new EmptyBorder(17, 18, 17, 18));
        countCard.setLayout(new BoxLayout(
                countCard, BoxLayout.Y_AXIS
        ));
        countCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        countCard.setMaximumSize(new Dimension(
                Integer.MAX_VALUE, 115
        ));

        JLabel countTitle = new JLabel("OPERATORI CONNESSI");
        countTitle.setFont(new Font("Arial", Font.BOLD, 10));
        countTitle.setForeground(MUTED);
        countTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        bigCountLabel.setFont(new Font("Arial", Font.BOLD, 38));
        bigCountLabel.setForeground(TEXT);
        bigCountLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        countCard.add(countTitle);
        countCard.add(Box.createVerticalStrut(5));
        countCard.add(bigCountLabel);

        JLabel stateTitle = new JLabel("STATO SESSIONE");
        stateTitle.setFont(new Font("Arial", Font.BOLD, 10));
        stateTitle.setForeground(MUTED);
        stateTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        statusLabel.setFont(new Font("Arial", Font.BOLD, 16));
        statusLabel.setForeground(STEEL);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JSeparator separator = new JSeparator();
        separator.setForeground(BORDER);
        separator.setBackground(BORDER);
        separator.setMaximumSize(new Dimension(
                Integer.MAX_VALUE, 1
        ));

        JLabel instructionsTitle = new JLabel("PROSSIMI PASSAGGI");
        instructionsTitle.setFont(new Font("Arial", Font.BOLD, 10));
        instructionsTitle.setForeground(MUTED);
        instructionsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel steps = createStepsPanel();

        JPanel waitingCard = new JPanel(new BorderLayout(10, 10));
        waitingCard.setBackground(new Color(34, 47, 57));
        waitingCard.setBorder(new EmptyBorder(15, 14, 15, 14));
        waitingCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        waitingCard.setMaximumSize(new Dimension(
                Integer.MAX_VALUE, 90
        ));

        JLabel waitingIcon = new JLabel("◷");
        waitingIcon.setFont(new Font("Arial", Font.PLAIN, 26));
        waitingIcon.setForeground(STEEL);

        JPanel waitingText = new JPanel();
        waitingText.setOpaque(false);
        waitingText.setLayout(new BoxLayout(
                waitingText, BoxLayout.Y_AXIS
        ));

        JLabel waitingTitle = new JLabel("In attesa");
        waitingTitle.setFont(new Font("Arial", Font.BOLD, 13));
        waitingTitle.setForeground(TEXT);

        JLabel waitingDescription = new JLabel(
                "<html>L'amministratore avvierà<br>"
                + "la sessione quando sarà pronta.</html>"
        );
        waitingDescription.setFont(new Font("Arial", Font.PLAIN, 11));
        waitingDescription.setForeground(MUTED);

        waitingText.add(waitingTitle);
        waitingText.add(Box.createVerticalStrut(5));
        waitingText.add(waitingDescription);

        waitingCard.add(waitingIcon, BorderLayout.WEST);
        waitingCard.add(waitingText, BorderLayout.CENTER);

        panel.add(heading);
        panel.add(Box.createVerticalStrut(5));
        panel.add(headingDescription);
        panel.add(Box.createVerticalStrut(25));
        panel.add(countCard);
        panel.add(Box.createVerticalStrut(25));
        panel.add(stateTitle);
        panel.add(Box.createVerticalStrut(8));
        panel.add(statusLabel);
        panel.add(Box.createVerticalStrut(22));
        panel.add(separator);
        panel.add(Box.createVerticalStrut(22));
        panel.add(instructionsTitle);
        panel.add(Box.createVerticalStrut(13));
        panel.add(steps);
        panel.add(Box.createVerticalStrut(20));
        panel.add(waitingCard);

        return panel;
    }

    private JPanel createStepsPanel() {
        JPanel steps = new JPanel();
        steps.setOpaque(false);
        steps.setLayout(new BoxLayout(
                steps, BoxLayout.Y_AXIS
        ));
        steps.setAlignmentX(Component.LEFT_ALIGNMENT);

        steps.add(createStep(
                "01",
                "Preparazione",
                "Attendi gli altri partecipanti"
        ));

        steps.add(Box.createVerticalStrut(15));

        steps.add(createStep(
                "02",
                "Avvio sessione",
                "La partita verrà avviata"
        ));

        steps.add(Box.createVerticalStrut(15));

        steps.add(createStep(
                "03",
                "Briefing",
                "Riceverai le informazioni iniziali"
        ));

        return steps;
    }

    private JPanel createStep(
            String number,
            String title,
            String description
    ) {
        JPanel step = new JPanel(new BorderLayout(12, 0));
        step.setOpaque(false);
        step.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel numberLabel = new JLabel(number);
        numberLabel.setFont(new Font("Monospaced", Font.BOLD, 13));
        numberLabel.setForeground(STEEL);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(
                textPanel, BoxLayout.Y_AXIS
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 12));
        titleLabel.setForeground(TEXT);

        JLabel descriptionLabel = new JLabel(description);
        descriptionLabel.setFont(new Font("Arial", Font.PLAIN, 10));
        descriptionLabel.setForeground(MUTED);

        textPanel.add(titleLabel);
        textPanel.add(Box.createVerticalStrut(4));
        textPanel.add(descriptionLabel);

        step.add(numberLabel, BorderLayout.WEST);
        step.add(textPanel, BorderLayout.CENTER);

        return step;
    }

    // --------------------------------------------------
    // FOOTER
    // --------------------------------------------------

    private JPanel createFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JLabel brand = new JLabel("SOC: CONTROL ROOM");
        brand.setFont(new Font("Arial", Font.BOLD, 11));
        brand.setForeground(STEEL);

        JLabel version = new JLabel("SALA OPERATIVA MULTIPLAYER");
        version.setFont(new Font("Arial", Font.PLAIN, 9));
        version.setForeground(MUTED);

        left.add(brand);
        left.add(Box.createVerticalStrut(4));
        left.add(version);

        styleButton(
                leaveButton,
                new Color(55, 65, 73),
                new Color(75, 87, 96),
                TEXT
        );

        leaveButton.setPreferredSize(new Dimension(190, 43));

        footer.add(left, BorderLayout.WEST);
        footer.add(leaveButton, BorderLayout.EAST);

        return footer;
    }

    // --------------------------------------------------
    // BUTTON STYLE
    // --------------------------------------------------

    private void styleButton(
            JButton button,
            Color normal,
            Color hover,
            Color foreground
    ) {
        button.setFont(new Font("Arial", Font.BOLD, 11));
        button.setForeground(foreground);
        button.setBackground(normal);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(
                Cursor.HAND_CURSOR
        ));

        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                button.setBackground(hover);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                button.setBackground(normal);
            }
        });
    }

    // --------------------------------------------------
    // PLAYER LIST
    // --------------------------------------------------

    public void updatePlayers(String players) {
        playerModel.clear();

        if (players != null && !players.isBlank()) {
            for (String player : players.split(", ")) {
                if (!player.isBlank()) {
                    playerModel.addElement(player.trim());
                }
            }
        }

        int count = playerModel.size();

        playerCountLabel.setText(
                count + (count == 1 ? " GIOCATORE" : " GIOCATORI")
        );

        animatePlayerCount(count);

        statusLabel.setForeground(STEEL);
        statusLabel.setText("In attesa di giocatori");
    }

    private void animatePlayerCount(int target) {
        if (countAnimationTimer != null
                && countAnimationTimer.isRunning()) {
            countAnimationTimer.stop();
        }

        int start;

        try {
            start = Integer.parseInt(bigCountLabel.getText());
        } catch (NumberFormatException e) {
            start = 0;
        }

        final int initialValue = start;
        final long startTime = System.nanoTime();
        final int duration = 300;

        countAnimationTimer = new Timer(16, e -> {
            double elapsed = (System.nanoTime() - startTime)
                    / 1_000_000.0;

            double progress = Math.min(1.0, elapsed / duration);

            // Interpolazione morbida.
            double eased = 1 - Math.pow(1 - progress, 3);

            int current = (int) Math.round(
                    initialValue + (target - initialValue) * eased
            );

            bigCountLabel.setText(String.valueOf(current));

            if (progress >= 1.0) {
                bigCountLabel.setText(String.valueOf(target));
                countAnimationTimer.stop();
            }
        });

        countAnimationTimer.start();
    }

    private void handleServerMessage(Message message) {
        switch (message.type()) {

            case LOBBY_UPDATE ->
                    updatePlayers(message.content());

            case ERROR -> {
                statusLabel.setForeground(ERROR);
                statusLabel.setText(message.content());
            }

            case GAME_START -> {
                statusLabel.setForeground(STEEL);
                statusLabel.setText("Avvio della sessione...");
            }

            default -> System.out.println(
                    "Messaggio server: " + message.type()
            );
        }
    }

    private void showDisconnected() {
        statusLabel.setForeground(ERROR);
        statusLabel.setText("Connessione persa");

        connectionLabel.setText("SERVER DISCONNESSO");
        connectionLabel.setForeground(ERROR);
        connectionDot.setForeground(ERROR);
    }

    // --------------------------------------------------
    // PLAYER RENDERER
    // --------------------------------------------------

    private class PlayerRenderer extends JPanel
            implements ListCellRenderer<String> {

        private final JLabel avatar = new JLabel();
        private final JLabel name = new JLabel();
        private final JLabel role = new JLabel();

        PlayerRenderer() {
            setLayout(new BorderLayout(13, 0));
            setBorder(new EmptyBorder(8, 12, 8, 12));

            avatar.setPreferredSize(new Dimension(38, 38));
            avatar.setHorizontalAlignment(SwingConstants.CENTER);
            avatar.setFont(new Font("Arial", Font.BOLD, 15));
            avatar.setOpaque(true);
            avatar.setBackground(new Color(49, 67, 80));
            avatar.setForeground(new Color(190, 210, 222));

            JPanel details = new JPanel();
            details.setOpaque(false);
            details.setLayout(new BoxLayout(
                    details, BoxLayout.Y_AXIS
            ));

            name.setFont(new Font("Arial", Font.BOLD, 14));
            name.setForeground(TEXT);
            name.setAlignmentX(Component.CENTER_ALIGNMENT);
            name.setHorizontalAlignment(SwingConstants.CENTER);

            role.setFont(new Font("Arial", Font.PLAIN, 10));
            role.setForeground(MUTED);
            role.setAlignmentX(Component.CENTER_ALIGNMENT);
            role.setHorizontalAlignment(SwingConstants.CENTER);

            details.add(name);
            details.add(Box.createVerticalStrut(5));
            details.add(role);

            add(avatar, BorderLayout.WEST);
            add(details, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends String> list,
                String value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            boolean currentPlayer = value.equalsIgnoreCase(nickname);

            name.setText(value);
            avatar.setText(
                    value.isEmpty()
                            ? "?"
                            : value.substring(0, 1).toUpperCase()
            );

            role.setText(
                    currentPlayer
                            ? "TU · OPERATORE CONNESSO"
                            : "OPERATORE CONNESSO"
            );

            if (currentPlayer) {
                avatar.setBackground(new Color(65, 88, 103));
                role.setForeground(STEEL);
            } else {
                avatar.setBackground(new Color(49, 67, 80));
                role.setForeground(MUTED);
            }

            setBackground(
                    isSelected
                            ? new Color(51, 69, 82)
                            : PANEL_LIGHT
            );

            setOpaque(true);

            return this;
        }
    }

    // --------------------------------------------------
    // ROUNDED PANEL
    // --------------------------------------------------

    private static class RoundedPanel extends JPanel {

        private final int radius;
        private final Color color;

        RoundedPanel(int radius, Color color) {
            this.radius = radius;
            this.color = color;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            g2.setColor(color);
            g2.fill(new RoundRectangle2D.Double(
                    0, 0, getWidth() - 1, getHeight() - 1,
                    radius, radius
            ));

            g2.setColor(BORDER);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Double(
                    0, 0, getWidth() - 1, getHeight() - 1,
                    radius, radius
            ));

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // --------------------------------------------------
    // ANIMATED BACKGROUND
    // --------------------------------------------------

    private static class AnimatedBackground extends JPanel {

        private double phase = 0;

        private final Timer animationTimer;

        private final double[][] nodes = {
                {0.08, 0.18}, {0.19, 0.38}, {0.31, 0.14},
                {0.43, 0.29}, {0.56, 0.12}, {0.68, 0.34},
                {0.81, 0.16}, {0.92, 0.39}, {0.13, 0.76},
                {0.29, 0.88}, {0.49, 0.72}, {0.69, 0.86},
                {0.85, 0.68}
        };

        private final int[][] links = {
                {0, 1}, {1, 2}, {1, 3}, {2, 3},
                {3, 4}, {3, 5}, {4, 5}, {5, 6},
                {5, 7}, {6, 7}, {8, 9}, {9, 10},
                {10, 11}, {11, 12}, {8, 10}, {10, 12}
        };

        AnimatedBackground() {
            setOpaque(true);
            setBackground(BACKGROUND);
            setDoubleBuffered(true);

            animationTimer = new Timer(40, e -> {
                phase += 0.018;

                if (phase > Math.PI * 2) {
                    phase = 0;
                }

                repaint();
            });
        }

        @Override
        public void addNotify() {
            super.addNotify();
            animationTimer.start();
        }

        @Override
        public void removeNotify() {
            animationTimer.stop();
            super.removeNotify();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            int w = getWidth();
            int h = getHeight();

            GradientPaint gradient = new GradientPaint(
                    0, 0, new Color(27, 40, 49),
                    w, h, BACKGROUND
            );

            g2.setPaint(gradient);
            g2.fillRect(0, 0, w, h);

            drawAmbientLight(g2, w, h);
            drawGrid(g2, w, h);
            drawNetwork(g2, w, h);
            drawScanLine(g2, w, h);

            g2.dispose();
        }

        private void drawAmbientLight(Graphics2D g, int w, int h) {
            float pulse = (float) (
                    0.5 + 0.5 * Math.sin(phase)
            );

            int alpha = 8 + (int) (pulse * 8);

            g.setColor(new Color(95, 137, 160, alpha));
            g.fill(new Ellipse2D.Double(
                    w * 0.55,
                    h * 0.15,
                    w * 0.45,
                    h * 0.55
            ));
        }

        private void drawGrid(Graphics2D g, int w, int h) {
            g.setColor(new Color(130, 160, 180, 10));
            g.setStroke(new BasicStroke(1f));

            for (int x = 0; x < w; x += 50) {
                g.drawLine(x, 0, x, h);
            }

            for (int y = 0; y < h; y += 50) {
                g.drawLine(0, y, w, y);
            }
        }

        private void drawNetwork(Graphics2D g, int w, int h) {
            Point[] points = new Point[nodes.length];

            for (int i = 0; i < nodes.length; i++) {
                double offsetX = Math.sin(phase + i * 0.7) * 5;
                double offsetY = Math.cos(phase + i * 0.8) * 5;

                points[i] = new Point(
                        (int) (nodes[i][0] * w + offsetX),
                        (int) (nodes[i][1] * h + offsetY)
                );
            }

            g.setStroke(new BasicStroke(1f));
            g.setColor(new Color(135, 170, 190, 32));

            for (int[] link : links) {
                Point a = points[link[0]];
                Point b = points[link[1]];

                g.draw(new Line2D.Double(a, b));
            }

            for (int i = 0; i < points.length; i++) {
                Point point = points[i];

                float pulse = (float) (
                        0.5 + 0.5 * Math.sin(phase * 1.5 + i)
                );

                int alpha = 35 + (int) (pulse * 35);

                g.setColor(new Color(155, 190, 207, alpha));
                g.fill(new Ellipse2D.Double(
                        point.x - 2.5,
                        point.y - 2.5,
                        5, 5
                ));

                g.setColor(new Color(140, 178, 198, alpha / 2));
                g.draw(new Ellipse2D.Double(
                        point.x - 7,
                        point.y - 7,
                        14, 14
                ));
            }
        }

        private void drawScanLine(Graphics2D g, int w, int h) {
            double position = phase / (Math.PI * 2);
            int y = (int) (position * h);

            GradientPaint scan = new GradientPaint(
                    0, y - 12,
                    new Color(120, 160, 185, 0),
                    0, y,
                    new Color(120, 160, 185, 18)
            );

            g.setPaint(scan);
            g.fillRect(0, y - 12, w, 12);
        }
    }

    // --------------------------------------------------
    // CUSTOM EXIT DIALOG
    // --------------------------------------------------

    private void leaveLobby() {
        JDialog dialog = new JDialog(
                this,
                "Uscita dalla lobby",
                true
        );

        dialog.setUndecorated(true);
        dialog.setDefaultCloseOperation(
                JDialog.DISPOSE_ON_CLOSE
        );

        JPanel background = new JPanel(new GridBagLayout());
        background.setBackground(BACKGROUND);
        background.setBorder(new EmptyBorder(12, 12, 12, 12));

        RoundedPanel card = new RoundedPanel(18, PANEL);
        card.setLayout(new BorderLayout(15, 15));
        card.setBorder(new EmptyBorder(25, 27, 24, 27));
        card.setPreferredSize(new Dimension(430, 225));

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(
                textPanel, BoxLayout.Y_AXIS
        ));

        JLabel brand = new JLabel("SOC: CONTROL ROOM");
        brand.setFont(new Font("Arial", Font.BOLD, 10));
        brand.setForeground(STEEL);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel("Uscire dalla lobby?");
        title.setFont(new Font("Arial", Font.BOLD, 23));
        title.setForeground(TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel description = new JLabel(
                "<html>La connessione al server verrà chiusa.<br>"
                + "Vuoi davvero abbandonare la sessione?</html>"
        );
        description.setFont(new Font("Arial", Font.PLAIN, 12));
        description.setForeground(MUTED);
        description.setAlignmentX(Component.LEFT_ALIGNMENT);

        textPanel.add(brand);
        textPanel.add(Box.createVerticalStrut(14));
        textPanel.add(title);
        textPanel.add(Box.createVerticalStrut(10));
        textPanel.add(description);

        JPanel buttons = new JPanel(new FlowLayout(
                FlowLayout.RIGHT, 10, 0
        ));
        buttons.setOpaque(false);

        JButton cancelButton = new JButton("RIMANI");
        JButton confirmButton = new JButton("ESCI");

        styleButton(
                cancelButton,
                new Color(55, 65, 73),
                new Color(75, 87, 96),
                TEXT
        );

        styleButton(
                confirmButton,
                new Color(125, 75, 66),
                new Color(153, 88, 76),
                Color.WHITE
        );

        cancelButton.setPreferredSize(new Dimension(105, 39));
        confirmButton.setPreferredSize(new Dimension(105, 39));

        cancelButton.addActionListener(e -> dialog.dispose());

        confirmButton.addActionListener(e -> {
            dialog.dispose();
            networkClient.close();
            dispose();
        });

        buttons.add(cancelButton);
        buttons.add(confirmButton);

        card.add(textPanel, BorderLayout.CENTER);
        card.add(buttons, BorderLayout.SOUTH);

        background.add(card);

        dialog.setContentPane(background);
        dialog.getRootPane().registerKeyboardAction(
                e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
}
