package it.soc.client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;

public final class Dialogs {

    private Dialogs() {
    }

    public static boolean confirm(Frame owner, String title, String text, String cancel, String confirm) {
        boolean[] result = {false};

        JDialog dialog = new JDialog(owner, title, true);
        dialog.setUndecorated(true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel background = new JPanel(new GridBagLayout());
        background.setBackground(Theme.BACKGROUND);
        background.setBorder(new EmptyBorder(12, 12, 12, 12));

        UiKit.RoundedPanel card = new UiKit.RoundedPanel(18, Theme.PANEL);
        card.setLayout(new BorderLayout(15, 15));
        card.setBorder(new EmptyBorder(25, 27, 24, 27));
        card.setPreferredSize(new Dimension(440, 225));

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        textPanel.add(UiKit.left(UiKit.label("SOC: CONTROL ROOM", 10, Font.BOLD, Theme.STEEL)));
        textPanel.add(Box.createVerticalStrut(14));
        textPanel.add(UiKit.left(UiKit.label(title, 23, Font.BOLD, Theme.TEXT)));
        textPanel.add(Box.createVerticalStrut(10));
        textPanel.add(UiKit.left(UiKit.label("<html>" + text + "</html>", 12, Font.PLAIN, Theme.MUTED)));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);

        if (cancel != null) {
            JButton cancelButton = new JButton(cancel);
            UiKit.styleButton(cancelButton, new Color(55, 65, 73), new Color(75, 87, 96), Theme.TEXT);
            cancelButton.setPreferredSize(new Dimension(115, 39));
            cancelButton.addActionListener(e -> dialog.dispose());
            buttons.add(cancelButton);
        }

        JButton confirmButton = new JButton(confirm);
        UiKit.styleButton(confirmButton, new Color(125, 75, 66), new Color(153, 88, 76), Color.WHITE);
        confirmButton.setPreferredSize(new Dimension(115, 39));
        confirmButton.addActionListener(e -> {
            result[0] = true;
            dialog.dispose();
        });
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
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);

        return result[0];
    }
}
