package it.soc.client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.util.prefs.Preferences;

public class TutorialDialog extends JDialog {

    private static final String PREFERENCE = "tutorialSeen";

    private static final String[][] PAGES = {
            {
                    "Lo scopo del gioco",
                    "In ogni sessione un operatore è l'INSIDER: finge di essere un analista ma sta rubando i dati dell'azienda.",
                    "Gli ANALISTI (con 5 o più giocatori uno di loro è il FORENSE) devono scoprire chi è e fermarlo.",
                    "Il tuo ruolo è segreto e lo vedi solo tu nel briefing iniziale.",
                    "Tutto è simulato: nessuna azione tocca sistemi reali."
            },
            {
                    "Se sei un analista",
                    "ANALIZZA estrae voci dai log del sistema selezionato. Le prove restano private finché non le condividi.",
                    "ISOLA blocca un sistema per 20 secondi: se l'insider prova a rubare lì, l'esfiltrazione viene bloccata e lascia una traccia.",
                    "CONDIVIDI una prova per mostrarla a tutti come verificata. Il forense estrae più voci e può recuperare le tracce cancellate.",
                    "Gli obiettivi in basso danno punti a fine partita: analizza sistemi diversi, condividi prove, blocca un'esfiltrazione."
            },
            {
                    "Se sei l'insider",
                    "ESFILTRA porta fuori dati dal sistema scelto e può lasciare una traccia con la tua postazione.",
                    "CANCELLA toglie la tua ultima traccia, ma una rotazione dei log può tradirti. DEPISTA pianta una prova falsa contro un collega.",
                    "DISTURBA oscura un sistema per 15 secondi: gli analisti non possono leggerlo né isolarlo e le esfiltrazioni lì non lasciano tracce.",
                    "Ripetere due volte di fila la stessa mossa ti espone: il tempo di ricarica sale e le tracce sono più riconoscibili."
            },
            {
                    "Come si vince",
                    "L'insider vince se i dati esfiltrati arrivano al 100%, oppure se il voto finale non lo smaschera.",
                    "Gli analisti vincono se espellono l'insider con il voto, oppure se bloccano abbastanza tentativi di esfiltrazione (il contatore è nella barra in alto).",
                    "Dopo i primi 45 secondi puoi chiedere un VOTO ANTICIPATO: serve la richiesta di metà degli operatori. L'indagine si ferma e si vota subito.",
                    "Se espellete l'insider vincete subito, ma se espellete un innocente l'esfiltrazione sale del 10% e quel giocatore esce dalla partita. I voti anticipati sono al massimo 2."
            },
            {
                    "Chat, colori e comandi",
                    "Scrivi nel canale per parlare con tutti. Per un messaggio privato usa /w Nome messaggio.",
                    "La casella SOLO PROVE nasconde la chat e mostra solo le prove condivise.",
                    "Mappa: verde-grigio ONLINE, rosso ALLARME, tratteggiato ISOLATO, giallo MANUTENZIONE, verde HONEYPOT (solo analisti), punto interrogativo SEGNALE DISTURBATO.",
                    "Se perdi la connessione hai 45 secondi per rientrare con lo stesso nickname."
            }
    };

    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final JLabel pageLabel = UiKit.label("1 / " + PAGES.length, 11, Font.BOLD, Theme.MUTED);
    private final JButton back = new JButton("INDIETRO");
    private final JButton next = new JButton("AVANTI");

    private int page;

    private TutorialDialog(Frame owner) {
        super(owner, "SOC: CONTROL ROOM - Come si gioca", true);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(640, 500);
        setLocationRelativeTo(owner);
        setResizable(false);

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(Theme.BACKGROUND_LIGHT);
        root.setBorder(new EmptyBorder(22, 28, 20, 28));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(UiKit.label("COME SI GIOCA", 12, Font.BOLD, Theme.STEEL), BorderLayout.WEST);
        header.add(pageLabel, BorderLayout.EAST);

        body.setOpaque(false);

        for (String[] data : PAGES) {
            body.add(createPage(data), data[0]);
        }

        UiKit.styleButton(back, new Color(55, 65, 73), new Color(75, 87, 96), Theme.TEXT);
        UiKit.styleButton(next, Theme.STEEL, Theme.STEEL_HOVER, new Color(20, 29, 36));
        back.setPreferredSize(new Dimension(120, 40));
        next.setPreferredSize(new Dimension(140, 40));

        back.addActionListener(e -> show(page - 1));
        next.addActionListener(e -> {
            if (page == PAGES.length - 1) {
                dispose();
            } else {
                show(page + 1);
            }
        });

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setOpaque(false);
        footer.add(back);
        footer.add(next);

        root.add(header, BorderLayout.NORTH);
        root.add(body, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
        show(0);
    }

    private JPanel createPage(String[] data) {
        UiKit.RoundedPanel card = new UiKit.RoundedPanel(16, Theme.PANEL, Theme.BORDER_SOFT);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(22, 24, 18, 24));

        card.add(UiKit.left(UiKit.label(data[0], 22, Font.BOLD, Theme.TEXT)));
        card.add(Box.createVerticalStrut(16));

        for (int i = 1; i < data.length; i++) {
            JPanel row = new JPanel(new BorderLayout(12, 0));
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            JComponent dot = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Theme.STEEL);
                    g2.fill(new Ellipse2D.Double(0, 8, 6, 6));
                    g2.dispose();
                }
            };
            dot.setPreferredSize(new Dimension(8, 20));

            row.add(dot, BorderLayout.WEST);
            row.add(ScrollableList.wrap(data[i], 13, Font.PLAIN, Theme.mix(Theme.TEXT, Theme.MUTED, 0.2)),
                    BorderLayout.CENTER);

            card.add(row);
            card.add(Box.createVerticalStrut(12));
        }

        card.add(Box.createVerticalGlue());
        return card;
    }

    private void show(int index) {
        page = Math.max(0, Math.min(PAGES.length - 1, index));
        cards.show(body, PAGES[page][0]);
        pageLabel.setText((page + 1) + " / " + PAGES.length);
        back.setEnabled(page > 0);
        next.setText(page == PAGES.length - 1 ? "CHIUDI" : "AVANTI");
    }

    public static void open(Frame owner) {
        new TutorialDialog(owner).setVisible(true);
        markSeen();
    }

    public static void openOnce(Frame owner) {
        if (!hasSeen()) {
            open(owner);
        }
    }

    private static boolean hasSeen() {
        try {
            return Preferences.userNodeForPackage(TutorialDialog.class).getBoolean(PREFERENCE, false);
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static void markSeen() {
        try {
            Preferences.userNodeForPackage(TutorialDialog.class).putBoolean(PREFERENCE, true);
        } catch (RuntimeException e) {
            System.err.println("Preferenze non disponibili: " + e.getMessage());
        }
    }
}
