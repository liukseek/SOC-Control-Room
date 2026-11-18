
package it.soc.client;

import it.soc.common.Message;
import it.soc.common.MessageType;
import it.soc.common.Protocol;
import it.soc.common.model.LobbyState;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class ConnectionWindow extends JFrame {

    private static final Color BACKGROUND = new Color(13, 20, 26);
    private static final Color PANEL = new Color(24, 33, 41, 245);
    private static final Color STEEL = new Color(112, 142, 162);
    private static final Color STEEL_HOVER = new Color(145, 169, 185);
    private static final Color TEXT = new Color(237, 242, 245);
    private static final Color MUTED = new Color(155, 170, 181);

    private JTextField nicknameField;
    private JTextField serverField;
    private JLabel statusLabel;
    private JButton enterButton;

    private final NetworkClient networkClient = new NetworkClient();
    private final ClientSession session = new ClientSession(networkClient);

    private boolean joinRequested = false;
    private double animationPhase = 0;
    private Timer connectingTimer;

    public ConnectionWindow() {
        setTitle("SOC: CONTROL ROOM");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 600));
        setSize(1350, 820);
        setLocationRelativeTo(null);

        setContentPane(createBackground());

        session.setHandler(this::handleServerMessage);

        session.setDisconnectHandler(reason -> {
            stopConnectingAnimation();
            statusLabel.setForeground(new Color(220, 145, 125));
            statusLabel.setText("Connessione persa.");

            enterButton.setEnabled(true);
            enterButton.setText("ENTRA");
        });
    }

    private JPanel createBackground() {
        JPanel background = new JPanel(new GridBagLayout()) {

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
                        0, 0, new Color(29, 43, 53),
                        w, h, BACKGROUND
                );

                g2.setPaint(gradient);
                g2.fillRect(0, 0, w, h);

                drawGrid(g2, w, h);
                drawPlanet(g2, w, h);
                drawConnections(g2, w, h);
                drawBinary(g2, w, h);

                g2.setPaint(new RadialGradientPaint(
                        new Point2D.Double(w * 0.65, h * 0.5),
                        Math.max(w, h) * 0.75f,
                        new float[]{0f, 1f},
                        new Color[]{
                                new Color(0, 0, 0, 0),
                                new Color(0, 0, 0, 145)
                        }
                ));

                g2.fillRect(0, 0, w, h);
                g2.dispose();
            }
        };

        background.setBorder(new EmptyBorder(45, 75, 45, 75));

        Timer animation = new Timer(40, e -> {
            animationPhase += 0.04;
            background.repaint();
        });
        animation.start();

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.weighty = 1;
        gbc.anchor = GridBagConstraints.CENTER;

        gbc.gridx = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 60);
        content.add(createBrandPanel(), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 0, 0, 0);
        content.add(createLoginPanel(), gbc);

        background.add(content);

        return background;
    }

    private void drawGrid(Graphics2D g, int w, int h) {
        g.setColor(new Color(130, 160, 180, 12));
        g.setStroke(new BasicStroke(1));

        for (int x = 0; x < w; x += 45) {
            g.drawLine(x, 0, x, h);
        }

        for (int y = 0; y < h; y += 45) {
            g.drawLine(0, y, w, y);
        }
    }

    private void drawPlanet(Graphics2D g, int w, int h) {
        Graphics2D p = (Graphics2D) g.create();

        double cx = w * 0.73;
        double cy = h * 0.49;
        double r = h * 0.43;

        Ellipse2D globe = new Ellipse2D.Double(
                cx - r, cy - r, r * 2, r * 2
        );

        p.setPaint(new RadialGradientPaint(
                new Point2D.Double(cx - r * 0.35, cy - r * 0.35),
                (float) (r * 1.7),
                new float[]{0f, 0.55f, 1f},
                new Color[]{
                        new Color(91, 125, 145, 115),
                        new Color(44, 75, 94, 95),
                        new Color(17, 34, 46, 30)
                }
        ));

        p.fill(globe);

        p.setClip(globe);
        p.setColor(new Color(153, 185, 204, 45));
        p.setStroke(new BasicStroke(1.1f));

        for (int i = -3; i <= 3; i++) {
            double y = cy + i * r * 0.22;
            double width = Math.sqrt(
                    Math.max(0, r * r - Math.pow(y - cy, 2))
            );

            p.draw(new Ellipse2D.Double(
                    cx - width, y - r * 0.09,
                    width * 2, r * 0.18
            ));
        }

        for (int i = 0; i < 5; i++) {
            double width = r * (0.22 + i * 0.17);

            p.draw(new Ellipse2D.Double(
                    cx - width, cy - r,
                    width * 2, r * 2
            ));
        }

        p.setColor(new Color(150, 180, 195, 24));

        p.fillOval(
                (int) (cx - r * 0.65),
                (int) (cy - r * 0.48),
                (int) (r * 0.45),
                (int) (r * 0.34)
        );

        p.fillOval(
                (int) (cx + r * 0.08),
                (int) (cy - r * 0.25),
                (int) (r * 0.50),
                (int) (r * 0.62)
        );

        p.fillOval(
                (int) (cx - r * 0.30),
                (int) (cy + r * 0.28),
                (int) (r * 0.25),
                (int) (r * 0.36)
        );

        p.setClip(null);

        p.setColor(new Color(135, 169, 190, 105));
        p.setStroke(new BasicStroke(1.5f));
        p.draw(globe);

        p.dispose();
    }

    private void drawConnections(Graphics2D g, int w, int h) {
        Graphics2D p = (Graphics2D) g.create();

        double cx = w * 0.73;
        double cy = h * 0.49;
        double r = h * 0.43;

        int[][] nodes = {
                {-48, -25}, {-30, -48}, {-12, -12},
                {8, -37}, {29, -18}, {43, 12},
                {18, 35}, {-15, 28}, {-38, 12},
                {0, 8}, {34, -43}, {-5, 49}
        };

        List<Point2D.Double> points = new ArrayList<>();

        for (int[] node : nodes) {
            double x = cx + node[0] * r / 55.0;
            double y = cy + node[1] * r / 55.0;
            points.add(new Point2D.Double(x, y));
        }

        p.setStroke(new BasicStroke(1.2f));
        p.setColor(new Color(143, 180, 201, 65));

        int[][] links = {
                {0, 1}, {1, 2}, {2, 3}, {3, 4},
                {4, 5}, {5, 6}, {6, 7}, {7, 8},
                {8, 0}, {2, 9}, {9, 4}, {3, 10},
                {10, 5}, {7, 11}, {11, 9}
        };

        for (int[] link : links) {
            Point2D a = points.get(link[0]);
            Point2D b = points.get(link[1]);
            p.draw(new Line2D.Double(a, b));
        }

        for (int i = 0; i < points.size(); i++) {
            Point2D point = points.get(i);
            double pulse = (Math.sin(animationPhase * 1.6 + i * 0.9) + 1) / 2;

            p.setColor(new Color(173, 204, 220, 120 + (int) (pulse * 110)));
            p.fill(new Ellipse2D.Double(
                    point.getX() - 3,
                    point.getY() - 3,
                    6, 6
            ));

            double ring = 8 + pulse * 5;

            p.setColor(new Color(150, 184, 204, 30 + (int) ((1 - pulse) * 60)));
            p.draw(new Ellipse2D.Double(
                    point.getX() - ring,
                    point.getY() - ring,
                    ring * 2, ring * 2
            ));
        }

        for (int i = 0; i < links.length; i++) {
            double t = (animationPhase * 0.35 + i * 0.37) % 1.0;
            Point2D a = points.get(links[i][0]);
            Point2D b = points.get(links[i][1]);

            double x = a.getX() + (b.getX() - a.getX()) * t;
            double y = a.getY() + (b.getY() - a.getY()) * t;

            p.setColor(new Color(190, 220, 235, 70));
            p.fill(new Ellipse2D.Double(x - 4.5, y - 4.5, 9, 9));
            p.setColor(new Color(215, 235, 245, 220));
            p.fill(new Ellipse2D.Double(x - 2, y - 2, 4, 4));
        }

        p.setStroke(new BasicStroke(1.3f));
        p.setColor(new Color(128, 166, 190, 85));

        drawArc(p, w * 0.17, h * 0.27,
                w * 0.72, h * 0.37, h * 0.25);

        drawArc(p, w * 0.43, h * 0.82,
                w * 0.93, h * 0.28, h * 0.20);

        drawArc(p, w * 0.51, h * 0.18,
                w * 0.92, h * 0.69, h * 0.18);

        p.dispose();
    }

    private void drawArc(Graphics2D g,
                         double x1, double y1,
                         double x2, double y2,
                         double height) {

        QuadCurve2D curve = new QuadCurve2D.Double(
                x1, y1,
                (x1 + x2) / 2, (y1 + y2) / 2 - height,
                x2, y2
        );

        g.draw(curve);

        double t = (animationPhase * 0.18 + (x1 + y2) * 0.001) % 1.0;
        double cx = curve.getCtrlX();
        double cy = curve.getCtrlY();
        double px = (1 - t) * (1 - t) * x1 + 2 * (1 - t) * t * cx + t * t * x2;
        double py = (1 - t) * (1 - t) * y1 + 2 * (1 - t) * t * cy + t * t * y2;

        Color previous = g.getColor();
        g.setColor(new Color(170, 205, 225, 60));
        g.fill(new Ellipse2D.Double(px - 6, py - 6, 12, 12));
        g.setColor(new Color(205, 230, 242, 210));
        g.fill(new Ellipse2D.Double(px - 2.5, py - 2.5, 5, 5));
        g.setColor(previous);
    }

    private void drawBinary(Graphics2D g, int w, int h) {
        Graphics2D p = (Graphics2D) g.create();

        p.setFont(new Font("Monospaced", Font.PLAIN, 11));
        p.setColor(new Color(154, 184, 201, 35));

        String[] data = {
                "01001010 11001001",
                "10110100 00101101",
                "11001010 01010011",
                "00110101 10101001",
                "10010110 01101010",
                "01011001 11010010",
                "11100100 01010101",
                "00101011 10110100"
        };

        for (int i = 0; i < data.length; i++) {
            int x = 35 + (i % 4) * (w / 4);
            int y = 65 + (i / 4) * 34;

            p.drawString(data[i], x, y);
        }

        p.setFont(new Font("Monospaced", Font.PLAIN, 9));
        p.setColor(new Color(154, 184, 201, 22));

        for (int i = 0; i < 18; i++) {
            int x = 20 + (i * 73) % w;
            int y = 110 + (i * 97) % h;

            p.drawString(
                    (i % 2 == 0 ? "01010110" : "10100101"),
                    x, y
            );
        }

        p.dispose();
    }

    private JPanel createBrandPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel logo = new JLabel("SOC");
        logo.setFont(new Font("Arial", Font.BOLD, 24));
        logo.setForeground(STEEL);
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel line = new JPanel();
        line.setBackground(STEEL);
        line.setMaximumSize(new Dimension(55, 3));
        line.setPreferredSize(new Dimension(55, 3));
        line.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel(
                "<html>SOC:<br>CONTROL ROOM</html>"
        );

        title.setFont(new Font("Arial", Font.BOLD, 54));
        title.setForeground(TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(logo);
        panel.add(Box.createVerticalStrut(25));
        panel.add(line);
        panel.add(Box.createVerticalStrut(30));
        panel.add(title);

        return panel;
    }

    private JPanel createLoginPanel() {
        RoundedPanel panel = new RoundedPanel(20, PANEL);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(38, 38, 38, 38));
        panel.setPreferredSize(new Dimension(370, 390));

        JLabel heading = new JLabel("Accesso");
        heading.setFont(new Font("Arial", Font.BOLD, 29));
        heading.setForeground(TEXT);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel description = new JLabel("Entra nella sala operativa");
        description.setFont(new Font("Arial", Font.PLAIN, 14));
        description.setForeground(MUTED);
        description.setAlignmentX(Component.LEFT_ALIGNMENT);

        nicknameField = createField();
        serverField = createField();
        serverField.setText("localhost");

        enterButton = createButton("ENTRA");

        statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        statusLabel.setForeground(MUTED);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(heading);
        panel.add(Box.createVerticalStrut(8));
        panel.add(description);
        panel.add(Box.createVerticalStrut(35));

        panel.add(createFieldLabel("NICKNAME"));
        panel.add(Box.createVerticalStrut(9));
        panel.add(nicknameField);

        panel.add(Box.createVerticalStrut(23));

        panel.add(createFieldLabel("SERVER"));
        panel.add(Box.createVerticalStrut(9));
        panel.add(serverField);

        panel.add(Box.createVerticalStrut(28));
        panel.add(enterButton);
        panel.add(Box.createVerticalStrut(14));
        panel.add(statusLabel);

        enterButton.addActionListener(e -> connect());
        nicknameField.addActionListener(e -> serverField.requestFocusInWindow());
        serverField.addActionListener(e -> connect());

        return panel;
    }

    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Arial", Font.BOLD, 11));
        label.setForeground(MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JTextField createField() {
        JTextField field = new JTextField();

        field.setFont(new Font("Arial", Font.PLAIN, 14));
        field.setForeground(TEXT);
        field.setCaretColor(TEXT);
        field.setBackground(new Color(35, 45, 53));

        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        new Color(75, 91, 103), 1
                ),
                new EmptyBorder(12, 13, 12, 13)
        ));

        field.setPreferredSize(new Dimension(294, 46));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);

        return field;
    }

    private JButton createButton(String text) {
        JButton button = new JButton(text);

        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setForeground(new Color(20, 29, 36));
        button.setBackground(STEEL);

        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(false);

        button.setCursor(Cursor.getPredefinedCursor(
                Cursor.HAND_CURSOR
        ));

        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setPreferredSize(new Dimension(294, 49));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 49));

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(STEEL_HOVER);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(STEEL);
            }
        });

        return button;
    }

    private void connect() {
        String nickname = nicknameField.getText().trim();
        String server = serverField.getText().trim();

        if (nickname.isEmpty()) {
            connectionError("Inserisci un nickname.");
            nicknameField.requestFocus();
            return;
        }

        if (nickname.length() > 20) {
            connectionError("Massimo 20 caratteri.");
            nicknameField.requestFocus();
            return;
        }

        if (server.isEmpty()) {
            connectionError("Inserisci il server.");
            serverField.requestFocus();
            return;
        }

        if (networkClient.isConnected()) {
            connectionError("Sei già connesso.");
            return;
        }

        joinRequested = false;
        session.setNickname(nickname);

        enterButton.setEnabled(false);
        enterButton.setText("CONNESSIONE");
        startConnectingAnimation();

        statusLabel.setForeground(STEEL);
        statusLabel.setText("Connessione al server in corso...");

        new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground() throws Exception {
                networkClient.connect(server, Protocol.DEFAULT_PORT);

                networkClient.sendMessage(new Message(
                        MessageType.CONNECT,
                        nickname,
                        ""
                ));

                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    networkClient.close();
                    connectionError("Connessione interrotta.");
                } catch (ExecutionException e) {
                    networkClient.close();

                    Throwable cause = e.getCause();

                    if (cause instanceof IOException) {
                        connectionError("Impossibile raggiungere il server.");
                    } else {
                        connectionError("Errore durante la connessione.");
                    }
                }
            }

        }.execute();
    }

    private void handleServerMessage(Message message) {

        switch (message.type()) {

            case PONG -> {
                if (joinRequested) {
                    return;
                }

                joinRequested = true;

                statusLabel.setForeground(STEEL);
                statusLabel.setText("Server raggiunto. Accesso alla lobby...");

                try {
                    String nickname = nicknameField.getText().trim();

                    networkClient.sendMessage(new Message(
                            MessageType.JOIN_LOBBY,
                            nickname,
                            nickname
                    ));

                } catch (IOException e) {
                    connectionError("Errore durante l'accesso alla lobby.");
                    networkClient.close();
                }
            }

            case LOBBY_UPDATE -> {
                stopConnectingAnimation();

                LobbyWindow lobbyWindow = new LobbyWindow(session);

                lobbyWindow.updateLobby(
                        Protocol.fromContent(message.content(), LobbyState.class)
                );

                lobbyWindow.setVisible(true);

                dispose();
            }

            case ERROR -> {
                connectionError(message.content());
                networkClient.close();
            }

            default -> System.out.println(
                    "Messaggio server: " + message.type()
            );
        }
    }

    private void startConnectingAnimation() {
        stopConnectingAnimation();

        int[] dots = {0};

        connectingTimer = new Timer(350, e -> {
            dots[0] = (dots[0] + 1) % 4;
            enterButton.setText("CONNESSIONE" + ".".repeat(dots[0]));
        });

        connectingTimer.start();
    }

    private void stopConnectingAnimation() {
        if (connectingTimer != null) {
            connectingTimer.stop();
            connectingTimer = null;
        }
    }

    private void connectionError(String error) {
        stopConnectingAnimation();
        statusLabel.setForeground(new Color(220, 145, 125));
        statusLabel.setText(error);

        enterButton.setEnabled(true);
        enterButton.setText("ENTRA");
    }

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
            g2.fillRoundRect(
                    0, 0, getWidth(), getHeight(),
                    radius, radius
            );

            g2.setColor(new Color(255, 255, 255, 18));
            g2.drawRoundRect(
                    0, 0, getWidth() - 1, getHeight() - 1,
                    radius, radius
            );

            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(
                        UIManager.getCrossPlatformLookAndFeelClassName()
                );
            } catch (Exception e) {
                System.err.println("Look and Feel non disponibile.");
            }

            new ConnectionWindow().setVisible(true);
        });
    }
}
