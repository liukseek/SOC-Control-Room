
package it.soc.client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class LobbyWindow extends JFrame {

    private static final Color BACKGROUND = new Color(13, 20, 26);
    private static final Color PANEL = new Color(24, 33, 41);
    private static final Color STEEL = new Color(112, 142, 162);
    private static final Color TEXT = new Color(237, 242, 245);
    private static final Color MUTED = new Color(155, 170, 181);

    private final DefaultListModel<String> playerModel =
            new DefaultListModel<>();

    private final JLabel playerCountLabel = new JLabel("0 giocatori");
    private final JLabel statusLabel = new JLabel("In attesa di giocatori...");
    private final JButton leaveButton = new JButton("ESCI");

    private final NetworkClient networkClient;
    private final String nickname;

    public LobbyWindow(NetworkClient networkClient, String nickname) {
        this.networkClient = networkClient;
        this.nickname = nickname;

        setTitle("SOC: CONTROL ROOM - Lobby");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1050, 700);
        setMinimumSize(new Dimension(850, 550));
        setLocationRelativeTo(null);

        setContentPane(createContent());

        leaveButton.addActionListener(e -> leaveLobby());

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                leaveLobby();
            }
        });
    }

    private JPanel createContent() {
        JPanel root = new JPanel(new BorderLayout(25, 25));
        root.setBackground(BACKGROUND);
        root.setBorder(new EmptyBorder(35, 45, 35, 45));

        root.add(createHeader(), BorderLayout.NORTH);
        root.add(createMainPanel(), BorderLayout.CENTER);
        root.add(createFooter(), BorderLayout.SOUTH);

        return root;
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));

        JLabel brand = new JLabel("SOC: CONTROL ROOM");
        brand.setFont(new Font("Arial", Font.BOLD, 13));
        brand.setForeground(STEEL);

        JLabel title = new JLabel("Sala d'attesa");
        title.setFont(new Font("Arial", Font.BOLD, 34));
        title.setForeground(TEXT);

        JLabel subtitle = new JLabel("In attesa dell'inizio della sessione");
        subtitle.setFont(new Font("Arial", Font.PLAIN, 14));
        subtitle.setForeground(MUTED);

        titles.add(brand);
        titles.add(Box.createVerticalStrut(13));
        titles.add(title);
        titles.add(Box.createVerticalStrut(6));
        titles.add(subtitle);

        JPanel userPanel = new JPanel();
        userPanel.setOpaque(false);
        userPanel.setLayout(new BoxLayout(userPanel, BoxLayout.Y_AXIS));

        JLabel userLabel = new JLabel("CONNESSO COME");
        userLabel.setFont(new Font("Arial", Font.BOLD, 10));
        userLabel.setForeground(MUTED);
        userLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JLabel userName = new JLabel(nickname);
        userName.setFont(new Font("Arial", Font.BOLD, 17));
        userName.setForeground(TEXT);
        userName.setAlignmentX(Component.RIGHT_ALIGNMENT);

        userPanel.add(userLabel);
        userPanel.add(Box.createVerticalStrut(7));
        userPanel.add(userName);

        header.add(titles, BorderLayout.WEST);
        header.add(userPanel, BorderLayout.EAST);

        return header;
    }

    private JPanel createMainPanel() {
        JPanel main = new JPanel(new GridBagLayout());
        main.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1;

        gbc.gridx = 0;
        gbc.weightx = 0.7;
        gbc.insets = new Insets(0, 0, 0, 12);
        main.add(createPlayersPanel(), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.3;
        gbc.insets = new Insets(0, 12, 0, 0);
        main.add(createInfoPanel(), gbc);

        return main;
    }

    private JPanel createPlayersPanel() {
        JPanel panel = createPanel();
        panel.setLayout(new BorderLayout(15, 15));
        panel.setBorder(new EmptyBorder(25, 25, 25, 25));

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);

        JLabel title = new JLabel("Giocatori");
        title.setFont(new Font("Arial", Font.BOLD, 21));
        title.setForeground(TEXT);

        playerCountLabel.setFont(new Font("Arial", Font.PLAIN, 13));
        playerCountLabel.setForeground(STEEL);

        heading.add(title, BorderLayout.WEST);
        heading.add(playerCountLabel, BorderLayout.EAST);

        JList<String> playerList = new JList<>(playerModel);
        playerList.setBackground(new Color(35, 45, 53));
        playerList.setForeground(TEXT);
        playerList.setFont(new Font("Arial", Font.PLAIN, 15));
        playerList.setSelectionBackground(new Color(65, 85, 100));
        playerList.setSelectionForeground(TEXT);
        playerList.setFixedCellHeight(48);
        playerList.setBorder(new EmptyBorder(8, 10, 8, 10));

        JScrollPane scrollPane = new JScrollPane(playerList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(new Color(35, 45, 53));

        panel.add(heading, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createInfoPanel() {
        JPanel panel = createPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(25, 22, 25, 22));

        JLabel title = new JLabel("Sessione");
        title.setFont(new Font("Arial", Font.BOLD, 21));
        title.setForeground(TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel stateLabel = new JLabel("STATO");
        stateLabel.setFont(new Font("Arial", Font.BOLD, 10));
        stateLabel.setForeground(MUTED);
        stateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        statusLabel.setFont(new Font("Arial", Font.BOLD, 15));
        statusLabel.setForeground(STEEL);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel description = new JLabel(
                "<html>La sessione inizierà<br>"
                + "quando l'amministratore<br>"
                + "avvierà la partita.</html>"
        );
        description.setFont(new Font("Arial", Font.PLAIN, 13));
        description.setForeground(MUTED);
        description.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(title);
        panel.add(Box.createVerticalStrut(35));
        panel.add(stateLabel);
        panel.add(Box.createVerticalStrut(9));
        panel.add(statusLabel);
        panel.add(Box.createVerticalStrut(25));
        panel.add(description);

        return panel;
    }

    private JPanel createFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);

        JLabel info = new JLabel("SOC: CONTROL ROOM");
        info.setFont(new Font("Arial", Font.PLAIN, 11));
        info.setForeground(MUTED);

        leaveButton.setFont(new Font("Arial", Font.BOLD, 12));
        leaveButton.setForeground(TEXT);
        leaveButton.setBackground(new Color(55, 65, 73));
        leaveButton.setOpaque(true);
        leaveButton.setContentAreaFilled(true);
        leaveButton.setBorderPainted(false);
        leaveButton.setFocusPainted(false);
        leaveButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        leaveButton.setPreferredSize(new Dimension(120, 42));

        footer.add(info, BorderLayout.WEST);
        footer.add(leaveButton, BorderLayout.EAST);

        return footer;
    }

    private JPanel createPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(PANEL);
        panel.setBorder(BorderFactory.createLineBorder(
                new Color(65, 80, 91), 1
        ));
        return panel;
    }

    public void updatePlayers(String players) {
        playerModel.clear();

        if (players != null && !players.isBlank()) {
            for (String player : players.split(", ")) {
                playerModel.addElement(player);
            }
        }

        playerCountLabel.setText(
                playerModel.size() + " giocatori"
        );
    }

    private void leaveLobby() {
        networkClient.close();
        dispose();
        System.exit(0);
    }
}
