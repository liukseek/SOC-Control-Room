package it.soc.client;

import it.soc.common.Message;
import it.soc.common.MessageType;
import it.soc.common.Protocol;
import it.soc.common.model.ActionResult;
import it.soc.common.model.ActionType;
import it.soc.common.model.ChatMessage;
import it.soc.common.model.EvidenceResult;
import it.soc.common.model.GameEvent;
import it.soc.common.model.GameResult;
import it.soc.common.model.GameState;
import it.soc.common.model.Phase;
import it.soc.common.model.PlayerAction;
import it.soc.common.model.PlayerInfo;
import it.soc.common.model.Role;
import it.soc.common.model.RoleInfo;
import it.soc.common.model.SystemNode;
import it.soc.common.model.SystemStatus;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameWindow extends JFrame {

    private final ClientSession session;
    private final RoleInfo roleInfo;
    private final Role role;
    private final Color accent;
    private final String nickname;

    private final Map<String, String> systemNames = new HashMap<>();
    private final Map<String, String> workstations = new HashMap<>();
    private final Map<ActionType, ActionButton> buttons = new EnumMap<>(ActionType.class);

    private final AnimatedBackdrop backdrop;
    private final NetworkMapPanel map;
    private final ThreatMeter threatMeter;
    private final TimerRing timerRing;
    private final EvidencePanel evidencePanel;
    private final FeedPanel feedPanel;
    private final VotePanel votePanel;
    private final ToastLayer toasts = new ToastLayer();
    private final PhaseBanner banner = new PhaseBanner();

    private final JLabel titleLabel = UiKit.label("Briefing", 28, Font.BOLD, Theme.TEXT);
    private final JLabel subtitleLabel = UiKit.label("Preparazione della sessione", 13, Font.PLAIN, Theme.MUTED);
    private final JLabel selectedName = UiKit.label("Nessun sistema selezionato", 14, Font.BOLD, Theme.TEXT);
    private final JLabel selectedDetails = UiKit.label("Clicca un nodo sulla mappa per scegliere il bersaglio delle azioni",
            11, Font.PLAIN, Theme.MUTED);
    private final JPanel rosterChips = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    private final CardLayout centerCards = new CardLayout();
    private final JPanel centerPanel = new JPanel(centerCards);

    private BriefingOverlay briefing;
    private ResultsOverlay results;
    private GameState state;
    private Phase lastPhase;
    private String rosterKey = "";
    private int lastExfiltration;
    private boolean finished;

    public GameWindow(ClientSession session, GameState initial) {
        this.session = session;
        this.roleInfo = session.role();
        this.role = roleInfo.role();
        this.accent = Theme.accent(role);
        this.nickname = session.nickname();

        setTitle("SOC: CONTROL ROOM - " + (role == Role.INSIDER ? "Insider" : "SOC Analyst") + " - " + nickname);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1440, 880);
        setMinimumSize(new Dimension(1200, 760));
        setLocationRelativeTo(null);

        backdrop = new AnimatedBackdrop(role == Role.INSIDER ? new Color(190, 90, 80) : new Color(95, 137, 160));
        map = new NetworkMapPanel(role);
        threatMeter = new ThreatMeter(role);
        timerRing = new TimerRing(accent);
        evidencePanel = new EvidencePanel(this::systemName, workstations::get);
        feedPanel = new FeedPanel(nickname, accent, this::systemName, workstations::get);
        votePanel = new VotePanel(nickname, accent);

        backdrop.setLayout(new BorderLayout(20, 18));
        backdrop.setBorder(new EmptyBorder(22, 30, 24, 30));
        backdrop.add(createHeader(), BorderLayout.NORTH);
        backdrop.add(createBody(), BorderLayout.CENTER);
        setContentPane(backdrop);

        JLayeredPane layers = getLayeredPane();
        layers.add(banner, JLayeredPane.POPUP_LAYER);
        layers.add(toasts, JLayeredPane.POPUP_LAYER);
        toasts.setTopOffset(118);

        layers.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                layoutOverlays();
            }
        });

        if (initial.phase() == Phase.BRIEFING) {
            briefing = new BriefingOverlay(roleInfo);
            briefing.setOnClose(() -> {
                getLayeredPane().remove(briefing);
                getLayeredPane().repaint();
            });
            layers.add(briefing, JLayeredPane.MODAL_LAYER);
        }

        wireActions();

        session.setHandler(this::handleMessage);
        session.setDisconnectHandler(reason -> onDisconnected());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (finished || Dialogs.confirm(GameWindow.this, "Abbandonare la partita?",
                        "La connessione verrà chiusa e la tua postazione<br>risulterà disconnessa.",
                        "RIMANI", "ESCI")) {
                    session.close();
                    System.exit(0);
                }
            }
        });

        applyState(initial);
        feedPanel.addNotice("Sessione avviata",
                "Sei collegato come " + nickname + " dalla postazione " + roleInfo.workstation() + ".",
                accent);
    }

    private void layoutOverlays() {
        Dimension size = getLayeredPane().getSize();

        for (Component component : new Component[]{banner, toasts, briefing, results}) {
            if (component != null) {
                component.setBounds(0, 0, size.width, size.height);
            }
        }
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(20, 0));
        header.setOpaque(false);

        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        brand.add(UiKit.label("SOC: CONTROL ROOM", 13, Font.BOLD, Theme.STEEL));
        brand.add(Box.createVerticalStrut(8));
        brand.add(titleLabel);
        brand.add(Box.createVerticalStrut(4));
        brand.add(subtitleLabel);

        JPanel meterHolder = new JPanel(new GridBagLayout());
        meterHolder.setOpaque(false);
        threatMeter.setPreferredSize(new Dimension(320, 52));
        meterHolder.add(threatMeter);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        right.setOpaque(false);

        JPanel identity = new JPanel();
        identity.setOpaque(false);
        identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));

        JLabel roleChip = EvidencePanel.pill(roleInfo.title(), accent);
        roleChip.setFont(Theme.font(11, Font.BOLD));
        roleChip.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JLabel name = UiKit.label(nickname, 18, Font.BOLD, Theme.TEXT);
        name.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JLabel ws = UiKit.label("POSTAZIONE " + roleInfo.workstation(), 10, Font.BOLD, Theme.MUTED);
        ws.setAlignmentX(Component.RIGHT_ALIGNMENT);

        identity.add(Box.createVerticalStrut(6));
        identity.add(roleChip);
        identity.add(Box.createVerticalStrut(7));
        identity.add(name);
        identity.add(Box.createVerticalStrut(4));
        identity.add(ws);

        right.add(timerRing);
        right.add(identity);

        header.add(brand, BorderLayout.WEST);
        header.add(meterHolder, BorderLayout.CENTER);
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
        gbc.weightx = 0.6;
        gbc.insets = new Insets(0, 0, 0, 10);
        body.add(createOperationsCard(), gbc);

        JPanel right = new JPanel(new GridBagLayout());
        right.setOpaque(false);

        GridBagConstraints r = new GridBagConstraints();
        r.fill = GridBagConstraints.BOTH;
        r.weightx = 1;
        r.gridx = 0;

        r.gridy = 0;
        r.weighty = 0.46;
        r.insets = new Insets(0, 0, 9, 0);
        right.add(evidencePanel, r);

        r.gridy = 1;
        r.weighty = 0.54;
        r.insets = new Insets(9, 0, 0, 0);
        right.add(feedPanel, r);

        gbc.gridx = 1;
        gbc.weightx = 0.4;
        gbc.insets = new Insets(0, 10, 0, 0);
        body.add(right, gbc);

        evidencePanel.setPreferredSize(new Dimension(420, 300));
        feedPanel.setPreferredSize(new Dimension(420, 340));

        return body;
    }

    private JPanel createOperationsCard() {
        UiKit.RoundedPanel card = new UiKit.RoundedPanel(18, Theme.PANEL);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(18, 22, 18, 22));
        card.setPreferredSize(new Dimension(760, 600));

        centerPanel.setOpaque(false);
        centerPanel.add(createMapView(), "MAP");
        centerPanel.add(votePanel, "VOTE");

        card.add(centerPanel, BorderLayout.CENTER);
        return card;
    }

    private JPanel createMapView() {
        JPanel view = new JPanel(new BorderLayout(0, 12));
        view.setOpaque(false);

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        legend.setOpaque(false);
        legend.add(legendItem("ONLINE", Theme.STEEL));
        legend.add(legendItem("ALLARME", Theme.DANGER));
        legend.add(legendItem("ISOLATO", new Color(170, 190, 205)));
        legend.add(legendItem("MANUTENZIONE", Theme.WARN));

        if (role != Role.INSIDER) {
            legend.add(legendItem("HONEYPOT", Theme.HONEY));
        }

        JPanel header = UiKit.cardHeader("Mappa della rete",
                role == Role.INSIDER
                        ? "Scegli il bersaglio. Gli isolamenti degli altri ti sono nascosti."
                        : "Topologia dei sistemi aziendali monitorati dal SOC",
                legend);
        header.setBorder(new EmptyBorder(0, 0, 0, 0));

        UiKit.RoundedPanel mapHolder = new UiKit.RoundedPanel(14, Theme.PANEL_DARK, Theme.BORDER_SOFT);
        mapHolder.setLayout(new BorderLayout());
        mapHolder.add(map);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));

        JPanel selection = new JPanel(new BorderLayout(10, 0));
        selection.setOpaque(false);
        selection.setAlignmentX(Component.LEFT_ALIGNMENT);
        selection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        JPanel selectionText = new JPanel();
        selectionText.setOpaque(false);
        selectionText.setLayout(new BoxLayout(selectionText, BoxLayout.Y_AXIS));
        selectionText.add(UiKit.label("BERSAGLIO", 10, Font.BOLD, Theme.MUTED));
        selectionText.add(Box.createVerticalStrut(3));

        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        nameRow.setOpaque(false);
        nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameRow.add(selectedName);
        nameRow.add(Box.createHorizontalStrut(10));
        nameRow.add(selectedDetails);

        selectionText.add(nameRow);
        selection.add(selectionText, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(1, 0, 10, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        actions.setPreferredSize(new Dimension(600, 58));

        addButton(actions, ActionType.ANALYZE, "ANALIZZA", "Estrai voci dai log", Theme.STEEL);
        addButton(actions, ActionType.ISOLATE, "ISOLA", "Blocca per 20 s", new Color(150, 175, 192));

        if (role == Role.INSIDER) {
            addButton(actions, ActionType.EXFILTRATE, "ESFILTRA", "+10% dati rubati", Theme.DANGER);
            addButton(actions, ActionType.WIPE, "CANCELLA", "Rimuovi una traccia", new Color(226, 140, 104));
            addButton(actions, ActionType.FRAME, "DEPISTA", "Incolpa un collega", new Color(214, 118, 150));
        }

        JPanel roster = new JPanel(new BorderLayout(10, 0));
        roster.setOpaque(false);
        roster.setAlignmentX(Component.LEFT_ALIGNMENT);
        roster.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        rosterChips.setOpaque(false);
        roster.add(UiKit.label("OPERATORI", 10, Font.BOLD, Theme.MUTED), BorderLayout.WEST);
        roster.add(rosterChips, BorderLayout.CENTER);

        bottom.add(selection);
        bottom.add(Box.createVerticalStrut(10));
        bottom.add(actions);
        bottom.add(Box.createVerticalStrut(14));
        bottom.add(roster);

        view.add(header, BorderLayout.NORTH);
        view.add(mapHolder, BorderLayout.CENTER);
        view.add(bottom, BorderLayout.SOUTH);

        return view;
    }

    private JComponent legendItem(String text, Color color) {
        JLabel label = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fill(new Ellipse2D.Double(0, getHeight() / 2.0 - 3.5, 7, 7));
                g2.dispose();
                super.paintComponent(g);
            }
        };

        label.setFont(Theme.font(9, Font.BOLD));
        label.setForeground(Theme.MUTED);
        label.setBorder(new EmptyBorder(0, 12, 0, 0));
        return label;
    }

    private void addButton(JPanel panel, ActionType action, String title, String hint, Color color) {
        ActionButton button = new ActionButton(action, title, hint, color);
        buttons.put(action, button);
        panel.add(button);
    }

    private void wireActions() {
        map.setSelectionListener(id -> refreshSelection());

        for (ActionButton button : buttons.values()) {
            button.setOnClick(() -> performAction(button.action(), button));
        }

        evidencePanel.setShareListener(evidence ->
                session.send(MessageType.SHARE_EVIDENCE, evidence.id()));

        feedPanel.setSendListener(text -> session.send(MessageType.CHAT, text));

        votePanel.setVoteListener(vote -> session.send(MessageType.SUBMIT_VOTE, vote));
    }

    private void performAction(ActionType action, ActionButton source) {
        String target = map.selected();

        if (target == null) {
            toasts.show("Nessun bersaglio", "Seleziona prima un sistema sulla mappa.", Theme.WARN);
            return;
        }

        if (action == ActionType.ANALYZE) {
            session.send(MessageType.REQUEST_EVIDENCE, target);
            return;
        }

        if (action == ActionType.FRAME) {
            showFrameMenu(source, target);
            return;
        }

        session.send(MessageType.PLAYER_ACTION, new PlayerAction(action, target, null));
    }

    private void showFrameMenu(Component source, String target) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(Theme.PANEL);
        menu.setBorder(BorderFactory.createLineBorder(Theme.alpha(Theme.DANGER, 160)));

        JLabel title = UiKit.label("  INCOLPA UN OPERATORE", 10, Font.BOLD, Theme.ERROR);
        title.setBorder(new EmptyBorder(8, 6, 6, 12));
        menu.add(title);

        boolean any = false;

        if (state != null) {
            for (PlayerInfo player : state.players()) {
                if (player.name().equals(nickname) || !player.connected()) {
                    continue;
                }

                any = true;

                JMenuItem item = new JMenuItem(player.name() + "   " + player.workstation());
                item.setFont(Theme.font(12, Font.BOLD));
                item.setForeground(Theme.TEXT);
                item.setBackground(Theme.PANEL);
                item.setBorder(new EmptyBorder(8, 14, 8, 20));
                item.addActionListener(e -> session.send(MessageType.PLAYER_ACTION,
                        new PlayerAction(ActionType.FRAME, target, player.name())));
                menu.add(item);
            }
        }

        if (!any) {
            menu.add(UiKit.label("  Nessun operatore disponibile", 11, Font.PLAIN, Theme.MUTED));
        }

        menu.show(source, 0, -menu.getPreferredSize().height - 6);
    }

    private void refreshSelection() {
        String id = map.selected();
        SystemNode node = id == null ? null : map.node(id);

        if (node == null) {
            selectedName.setText("Nessun sistema selezionato");
            selectedDetails.setText("Clicca un nodo sulla mappa per scegliere il bersaglio delle azioni");
        } else {
            selectedName.setText(node.name());
            selectedName.setForeground(Theme.TEXT);
            selectedDetails.setText(node.description() + "  ·  " + node.logCount() + " voci di log  ·  "
                    + statusName(node));
            selectedDetails.setForeground(node.status() == SystemStatus.MAINTENANCE ? Theme.WARN
                    : node.alert() ? Theme.ERROR : Theme.MUTED);
        }

        refreshButtons();
    }

    private String statusName(SystemNode node) {
        return switch (node.status()) {
            case ISOLATED -> "ISOLATO";
            case MAINTENANCE -> "IN MANUTENZIONE";
            case HONEYPOT -> "HONEYPOT ATTIVO";
            default -> node.alert() ? "ALLARME SIEM" : "ONLINE";
        };
    }

    private void refreshButtons() {
        boolean active = state != null && state.phase() == Phase.INVESTIGATION && !finished;
        boolean hasTarget = map.selected() != null;

        for (ActionButton button : buttons.values()) {
            button.setAvailable(active && hasTarget);

            if (state != null) {
                button.setCooldown(state.cooldowns().getOrDefault(button.action(), 0));
            }
        }
    }

    private String systemName(String id) {
        return systemNames.getOrDefault(id, id);
    }

    private void handleMessage(Message message) {
        try {
            switch (message.type()) {
                case GAME_UPDATE -> applyState(Protocol.fromContent(message.content(), GameState.class));
                case GAME_EVENT -> onEvent(Protocol.fromContent(message.content(), GameEvent.class));
                case ACTION_RESULT -> onActionResult(Protocol.fromContent(message.content(), ActionResult.class));
                case EVIDENCE_RESULT -> onEvidence(Protocol.fromContent(message.content(), EvidenceResult.class));
                case CHAT -> onChat(Protocol.fromContent(message.content(), ChatMessage.class));
                case GAME_OVER -> onGameOver(Protocol.fromContent(message.content(), GameResult.class));
                case GAME_START -> {
                    if (finished) {
                        GameWindow next = new GameWindow(session, Protocol.fromContent(message.content(), GameState.class));
                        next.setVisible(true);
                        dispose();
                    }
                }
                case ERROR -> toasts.show("Errore", message.content(), Theme.ERROR);
                default -> {
                }
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Messaggio non valido: " + e.getMessage());
        }
    }

    private void applyState(GameState next) {
        if (finished) {
            return;
        }

        state = next;

        for (SystemNode node : next.systems()) {
            systemNames.put(node.id(), node.name());
        }

        for (PlayerInfo player : next.players()) {
            workstations.put(player.name(), player.workstation());
        }

        map.update(next.systems());
        threatMeter.setValue(next.exfiltration());
        timerRing.setTime(next.secondsLeft(), next.phaseDuration());

        if (next.exfiltration() > lastExfiltration && role != Role.INSIDER) {
            backdrop.setAlarm(Math.min(1, next.exfiltration() / 100.0 + 0.3));
            Anim.delay(1500, () -> backdrop.setAlarm(next.exfiltration() >= 70 ? 0.5 : 0));
        }

        lastExfiltration = next.exfiltration();

        if (briefing != null && next.phase() == Phase.BRIEFING) {
            briefing.setCountdown(next.secondsLeft(), next.phaseDuration());
        }

        if (next.phase() != lastPhase) {
            onPhaseChange(next.phase());
            lastPhase = next.phase();
        }

        updateRoster(next.players());
        votePanel.update(next.players());
        refreshSelection();

        long connected = next.players().stream().filter(PlayerInfo::connected).count();
        subtitleLabel.setText(phaseSubtitle(next.phase()) + "  ·  " + connected + " operatori connessi");
    }

    private String phaseSubtitle(Phase phase) {
        return switch (phase) {
            case BRIEFING -> "Lettura del briefing";
            case INVESTIGATION -> role == Role.INSIDER ? "Esfiltra i dati senza farti notare" : "Analizza i log e trova l'insider";
            case VOTING -> "Discuti con il team e vota";
            default -> "Sessione conclusa";
        };
    }

    private void onPhaseChange(Phase phase) {
        switch (phase) {
            case BRIEFING -> titleLabel.setText("Briefing");
            case INVESTIGATION -> {
                titleLabel.setText("Indagine in corso");

                if (briefing != null && briefing.isVisible()) {
                    briefing.dismiss();
                }

                centerCards.show(centerPanel, "MAP");

                if (lastPhase != null) {
                    banner.show("INDAGINE AVVIATA",
                            role == Role.INSIDER ? "Porta fuori i dati. Nessuno deve sospettare di te."
                                    : "Trova l'insider prima che porti fuori i dati.",
                            accent);
                }

                if (map.selected() == null) {
                    map.select("DB");
                }
            }
            case VOTING -> {
                titleLabel.setText("Votazione");
                centerCards.show(centerPanel, "VOTE");
                banner.show("FASE DI VOTO", "Chi è l'insider? Decidete insieme.", Theme.WARN);
                toasts.show("Votazione aperta", "Scegli un operatore e conferma il voto.", Theme.WARN);
            }
            default -> {
            }
        }
    }

    private void updateRoster(List<PlayerInfo> players) {
        StringBuilder key = new StringBuilder();

        for (PlayerInfo player : players) {
            key.append(player.name()).append(player.connected()).append(player.voted()).append(';');
        }

        if (key.toString().equals(rosterKey)) {
            return;
        }

        rosterKey = key.toString();
        rosterChips.removeAll();

        for (PlayerInfo player : players) {
            Color color = player.connected() ? Theme.playerColor(player.name()) : Theme.alpha(Theme.MUTED, 120);
            String text = player.name() + (player.name().equals(nickname) ? " (tu)" : "")
                    + "  " + player.workstation()
                    + (state != null && state.phase() == Phase.VOTING && player.voted() ? "  ✓" : "");

            JLabel chip = EvidencePanel.pill(text, color);
            chip.setFont(Theme.font(10, Font.BOLD));

            if (!player.connected()) {
                chip.setText(player.name() + "  OFFLINE");
            }

            rosterChips.add(chip);
        }

        rosterChips.revalidate();
        rosterChips.repaint();
    }

    private void onEvent(GameEvent event) {
        feedPanel.addEvent(event);
        map.pulse(event.system());

        if ("PHASE".equals(event.kind())) {
            return;
        }

        toasts.show(event.title(), event.description(), Theme.severity(event.severity()));

        if ("SIEM_ALERT".equals(event.kind()) && role != Role.INSIDER) {
            backdrop.setAlarm(0.8);
            Anim.delay(1800, () -> backdrop.setAlarm(lastExfiltration >= 70 ? 0.5 : 0));
        }
    }

    private void onActionResult(ActionResult result) {
        if (result.action() != null) {
            ActionButton button = buttons.get(result.action());

            if (button != null && result.cooldown() > 0) {
                button.setCooldown(result.cooldown());
            }

            if (result.action() == ActionType.ANALYZE && result.success()) {
                return;
            }
        }

        Color color = result.success() ? Theme.GOOD : Theme.ERROR;
        toasts.show(result.success() ? "Azione completata" : "Azione non riuscita", result.message(), color);

        if (role == Role.INSIDER && result.action() != null
                && result.action() != ActionType.ANALYZE && result.action() != ActionType.ISOLATE) {
            feedPanel.addNotice("Azione riservata · visibile solo a te", result.message(),
                    result.success() ? new Color(226, 140, 104) : Theme.ERROR);
        }
    }

    private void onEvidence(EvidenceResult result) {
        evidencePanel.addEvidence(result.entries());
        map.pulse(result.system());

        long suspicious = result.entries().stream()
                .filter(e -> "HIGH".equals(e.severity()) || "CRITICAL".equals(e.severity()))
                .count();

        toasts.show("Analisi completata",
                result.entries().size() + " voci estratte da " + systemName(result.system())
                        + (suspicious > 0 ? ", " + suspicious + " sospette!" : ", nessuna anomalia evidente."),
                suspicious > 0 ? Theme.ERROR : Theme.STEEL);
    }

    private void onChat(ChatMessage message) {
        feedPanel.addChat(message);

        if (message.evidence() != null) {
            evidencePanel.markShared(message.evidence().id());
        }
    }

    private void onGameOver(GameResult result) {
        finished = true;
        timerRing.stop();
        feedPanel.setInputEnabled(false);
        evidencePanel.setShareEnabled(false);
        map.setInteractive(false);
        refreshButtons();
        backdrop.setAlarm(0);

        if (briefing != null) {
            getLayeredPane().remove(briefing);
            briefing = null;
        }

        results = new ResultsOverlay(result, role, nickname, this::backToLobby, () -> {
            session.close();
            System.exit(0);
        });

        getLayeredPane().add(results, JLayeredPane.MODAL_LAYER);
        layoutOverlays();
        getLayeredPane().revalidate();
        getLayeredPane().repaint();
    }

    private void backToLobby() {
        LobbyWindow lobby = new LobbyWindow(session);

        if (session.lobby() != null) {
            lobby.updateLobby(session.lobby());
        }

        lobby.setVisible(true);
        session.send(MessageType.JOIN_LOBBY, nickname);
        dispose();
    }

    private void onDisconnected() {
        if (results != null) {
            return;
        }

        finished = true;
        timerRing.stop();
        refreshButtons();
        feedPanel.setInputEnabled(false);

        Dialogs.confirm(this, "Connessione persa",
                "Il server non è più raggiungibile.<br>La partita non può continuare.", null, "ESCI");

        System.exit(0);
    }
}
