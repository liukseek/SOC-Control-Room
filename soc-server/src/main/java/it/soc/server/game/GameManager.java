package it.soc.server.game;

import it.soc.common.Message;
import it.soc.common.MessageType;
import it.soc.common.Protocol;
import it.soc.common.model.ActionResult;
import it.soc.common.model.ActionType;
import it.soc.common.model.ChatMessage;
import it.soc.common.model.Evidence;
import it.soc.common.model.EvidenceResult;
import it.soc.common.model.GameEvent;
import it.soc.common.model.GameResult;
import it.soc.common.model.GameState;
import it.soc.common.model.LobbyState;
import it.soc.common.model.Phase;
import it.soc.common.model.PlayerAction;
import it.soc.common.model.PlayerInfo;
import it.soc.common.model.Role;
import it.soc.common.model.SystemNode;
import it.soc.common.model.SystemStatus;
import it.soc.server.ConnectionManager;
import it.soc.server.GameConfig;
import it.soc.server.ServerLog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class GameManager {

    public static final int EXFILTRATION_STEP = 10;
    public static final int ISOLATION_SECONDS = 20;
    public static final int MAINTENANCE_SECONDS = 20;
    public static final int HONEYPOT_SECONDS = 30;

    private static final Map<ActionType, Integer> COOLDOWNS = Map.of(
            ActionType.ANALYZE, 6,
            ActionType.ISOLATE, 35,
            ActionType.EXFILTRATE, 18,
            ActionType.WIPE, 25,
            ActionType.FRAME, 35
    );

    private final ConnectionManager connections;
    private final GameConfig config;
    private final Random random = new Random();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "SOC-Game-Ticker");
                thread.setDaemon(true);
                return thread;
            });

    private final Map<String, PlayerState> players = new LinkedHashMap<>();
    private final Map<String, SystemState> systems = new LinkedHashMap<>();
    private final List<String> insiderLog = new ArrayList<>();
    private final Set<String> sharedEvidence = new HashSet<>();

    private Phase phase = Phase.LOBBY;
    private int secondsLeft;
    private int phaseDuration;
    private ScheduledFuture<?> tickTask;

    private int exfiltration;
    private int evidenceCounter;
    private int clock;
    private int elapsed;
    private int nextEventAt;
    private String lastEventKind;
    private boolean badgeAuditDone;
    private String lastExfilSystem;
    private GameResult lastResult;

    public GameManager(ConnectionManager connections, GameConfig config) {
        this.connections = connections;
        this.config = config;
    }

    public GameConfig config() {
        return config;
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    public synchronized Phase phase() {
        return phase;
    }

    public synchronized boolean isInGame() {
        return phase != Phase.LOBBY;
    }

    public synchronized boolean isPlaying(String clientId) {
        return phase != Phase.LOBBY && players.containsKey(clientId);
    }

    public synchronized GameResult lastResult() {
        return lastResult;
    }

    public synchronized LobbyState lobbyState() {
        return new LobbyState(
                connections.getPlayerNames(),
                connections.getHostName(),
                config.minPlayers(),
                phase != Phase.LOBBY
        );
    }

    public void broadcastLobby() {
        connections.broadcast(Protocol.message(
                MessageType.LOBBY_UPDATE, "server", lobbyState()
        ));

        notifyListeners();
    }

    public synchronized AdminSnapshot snapshot() {
        List<AdminSnapshot.Player> list = new ArrayList<>();

        for (PlayerState player : players.values()) {
            list.add(new AdminSnapshot.Player(
                    player.name(),
                    player.workstation(),
                    player.role(),
                    player.isConnected(),
                    player.vote() != null,
                    player.actions(),
                    player.evidence().size()
            ));
        }

        return new AdminSnapshot(
                phase,
                secondsLeft,
                phaseDuration,
                exfiltration,
                connections.getPlayerNames(),
                connections.getHostName(),
                list
        );
    }

    public synchronized String startGame(String requesterId) {
        if (phase != Phase.LOBBY) {
            return "Una partita è già in corso.";
        }

        if (requesterId != null && !requesterId.equals(connections.getHostId())) {
            return "Solo l'host può avviare la sessione.";
        }

        List<String> ids = connections.getLobbyClientIds();

        if (ids.size() < config.minPlayers()) {
            return "Servono almeno " + config.minPlayers() + " operatori per iniziare.";
        }

        resetSession();

        List<Integer> stations = new ArrayList<>();

        for (int i = 1; i <= 24; i++) {
            stations.add(i);
        }

        Collections.shuffle(stations, random);

        String insiderId = ids.get(random.nextInt(ids.size()));

        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);

            players.put(id, new PlayerState(
                    id,
                    connections.getPlayerName(id),
                    String.format("WS-%02d", stations.get(i)),
                    id.equals(insiderId) ? Role.INSIDER : Role.ANALYST
            ));
        }

        for (SystemState system : Scenario.createSystems()) {
            systems.put(system.id(), system);
        }

        clock = 2 * 3600 + 38 * 60;

        for (SystemState system : systems.values()) {
            for (int i = 0; i < 5; i++) {
                addNoise(system);
                clock += 11;
            }
        }

        phase = Phase.BRIEFING;
        phaseDuration = config.briefingSeconds();
        secondsLeft = phaseDuration;

        for (PlayerState player : players.values()) {
            connections.sendTo(player.clientId(), Protocol.message(
                    MessageType.ROLE_ASSIGN,
                    "server",
                    Scenario.roleInfo(player.role(), player.workstation())
            ));
        }

        for (PlayerState player : players.values()) {
            connections.sendTo(player.clientId(), Protocol.message(
                    MessageType.GAME_START, "server", stateFor(player)
            ));
        }

        tickTask = scheduler.scheduleAtFixedRate(
                this::tick, 1, 1, TimeUnit.SECONDS
        );

        ServerLog.game("Partita avviata con " + players.size()
                + " operatori. Insider: " + players.get(insiderId).name());

        connections.broadcast(Protocol.message(
                MessageType.LOBBY_UPDATE, "server", lobbyState()
        ));

        notifyListeners();
        return null;
    }

    public synchronized void stopGame() {
        if (phase == Phase.LOBBY) {
            return;
        }

        finish(null, "Sessione interrotta dall'amministratore.", null, Map.of());
    }

    private void resetSession() {
        players.clear();
        systems.clear();
        insiderLog.clear();
        sharedEvidence.clear();
        exfiltration = 0;
        evidenceCounter = 0;
        elapsed = 0;
        nextEventAt = 30;
        lastEventKind = null;
        badgeAuditDone = false;
        lastExfilSystem = null;
    }

    private void tick() {
        try {
            synchronized (this) {
                doTick();
            }

            notifyListeners();
        } catch (RuntimeException e) {
            ServerLog.error("Errore nel ciclo di gioco: " + e.getMessage());
        }
    }

    private void doTick() {
        if (phase == Phase.LOBBY) {
            return;
        }

        secondsLeft--;
        clock += 7;

        for (PlayerState player : players.values()) {
            player.tickCooldowns();
        }

        if (phase == Phase.INVESTIGATION) {
            elapsed++;

            for (SystemState system : systems.values()) {
                system.tick();
            }

            if (elapsed % 4 == 0) {
                addNoise(randomSystem());
            }

            if (elapsed >= nextEventAt && secondsLeft > 10) {
                triggerRandomEvent();
                nextEventAt = elapsed + 35 + random.nextInt(15);
            }
        }

        if (phase == Phase.VOTING && allVoted() && secondsLeft > 3) {
            secondsLeft = 3;
        }

        if (secondsLeft <= 0) {
            advancePhase();
        }

        if (phase != Phase.LOBBY) {
            broadcastState();
        }
    }

    private void advancePhase() {
        switch (phase) {
            case BRIEFING -> {
                phase = Phase.INVESTIGATION;
                phaseDuration = config.investigationSeconds();
                secondsLeft = phaseDuration;

                sendEvent(new GameEvent(
                        "PHASE",
                        "Indagine avviata",
                        "Avete " + formatDuration(phaseDuration)
                                + " per smascherare l'insider.",
                        null,
                        "INFO"
                ), p -> true);

                ServerLog.game("Fase di indagine iniziata.");
            }
            case INVESTIGATION -> {
                phase = Phase.VOTING;
                phaseDuration = config.votingSeconds();
                secondsLeft = phaseDuration;

                sendEvent(new GameEvent(
                        "PHASE",
                        "Fase di voto",
                        "Discutete e votate l'operatore sospetto entro "
                                + phaseDuration + " secondi.",
                        null,
                        "WARN"
                ), p -> true);

                ServerLog.game("Fase di voto iniziata.");
            }
            case VOTING -> finishVoting();
            default -> {
            }
        }
    }

    private boolean allVoted() {
        for (PlayerState player : players.values()) {
            if (player.isConnected() && player.vote() == null) {
                return false;
            }
        }

        return true;
    }

    private void finishVoting() {
        Map<String, Integer> votes = new LinkedHashMap<>();
        int skipped = 0;

        for (PlayerState player : players.values()) {
            if (player.isConnected()) {
                votes.put(player.name(), 0);
            }
        }

        for (PlayerState player : players.values()) {
            if (!player.isConnected() || player.vote() == null) {
                continue;
            }

            if (player.vote().isEmpty()) {
                skipped++;
            } else if (votes.containsKey(player.vote())) {
                votes.merge(player.vote(), 1, Integer::sum);
            }
        }

        String ejected = null;
        int best = 0;
        boolean tie = false;

        for (var entry : votes.entrySet()) {
            if (entry.getValue() > best) {
                best = entry.getValue();
                ejected = entry.getKey();
                tie = false;
            } else if (entry.getValue() == best && best > 0) {
                tie = true;
            }
        }

        if (tie || best <= skipped) {
            ejected = null;
        }

        if (skipped > 0) {
            votes.put("Astenuti", skipped);
        }

        PlayerState insider = insider();
        String reason;
        Role winner;

        if (ejected == null) {
            winner = Role.INSIDER;
            reason = "Nessun operatore è stato espulso: l'insider resta nella rete.";
        } else if (insider != null && ejected.equals(insider.name())) {
            winner = Role.ANALYST;
            reason = "L'insider è stato identificato ed espulso dalla rete.";
        } else {
            winner = Role.INSIDER;
            reason = ejected + " era innocente. L'insider si è confuso tra gli analisti.";
        }

        finish(winner, reason, ejected, votes);
    }

    private void finish(Role winner, String reason, String ejected, Map<String, Integer> votes) {
        PlayerState insider = insider();

        int collected = 0;

        for (PlayerState player : players.values()) {
            collected += player.evidence().size();
        }

        lastResult = new GameResult(
                winner,
                reason,
                insider == null ? null : insider.name(),
                ejected,
                votes,
                exfiltration,
                collected,
                sharedEvidence.size(),
                List.copyOf(insiderLog)
        );

        Message message = Protocol.message(MessageType.GAME_OVER, "server", lastResult);

        for (PlayerState player : players.values()) {
            if (player.isConnected()) {
                connections.sendTo(player.clientId(), message);
            }
        }

        if (tickTask != null) {
            tickTask.cancel(false);
            tickTask = null;
        }

        phase = Phase.LOBBY;
        secondsLeft = 0;
        players.clear();

        ServerLog.game("Partita terminata. Vincitore: "
                + (winner == null ? "nessuno" : winner) + " - " + reason);

        connections.broadcast(Protocol.message(
                MessageType.LOBBY_UPDATE, "server", lobbyState()
        ));
    }

    public synchronized void handleAction(String clientId, PlayerAction action) {
        PlayerState player = players.get(clientId);

        if (player == null || action == null || action.action() == null) {
            return;
        }

        if (phase != Phase.INVESTIGATION) {
            reply(player, action.action(), false, "Le azioni sono disponibili solo durante l'indagine.");
            return;
        }

        if (!player.canUse(action.action())) {
            reply(player, action.action(), false, "Azione non autorizzata per il tuo ruolo.");
            ServerLog.warn("Azione non autorizzata da " + player.name() + ": " + action.action());
            return;
        }

        if (player.cooldown(action.action()) > 0) {
            reply(player, action.action(), false, "Azione in ricarica.");
            return;
        }

        SystemState system = action.target() == null ? null : systems.get(action.target());

        if (system == null) {
            reply(player, action.action(), false, "Seleziona un sistema valido.");
            return;
        }

        player.countAction();

        switch (action.action()) {
            case ANALYZE -> analyze(player, system);
            case ISOLATE -> isolate(player, system);
            case EXFILTRATE -> exfiltrate(player, system);
            case WIPE -> wipe(player, system);
            case FRAME -> frame(player, system, action.subject());
        }

        if (phase != Phase.LOBBY) {
            sendState(player);
        }

        notifyListeners();
    }

    public synchronized void handleEvidenceRequest(String clientId, String systemId) {
        handleAction(clientId, new PlayerAction(ActionType.ANALYZE, systemId, null));
    }

    private void analyze(PlayerState player, SystemState system) {
        if (system.inMaintenance()) {
            reply(player, ActionType.ANALYZE, false, system.name() + " è in manutenzione.");
            return;
        }

        List<LogEntry> visible = system.visibleLog();
        List<LogEntry> suspicious = new ArrayList<>();
        List<LogEntry> others = new ArrayList<>();

        for (LogEntry entry : visible) {
            if (entry.isSuspicious()) {
                suspicious.add(entry);
            } else {
                others.add(entry);
            }
        }

        Collections.shuffle(suspicious, random);
        Collections.shuffle(others, random);

        List<LogEntry> picked = new ArrayList<>();

        for (LogEntry entry : suspicious) {
            if (picked.size() < 4 && random.nextDouble() < 0.7) {
                picked.add(entry);
            }
        }

        for (LogEntry entry : others) {
            if (picked.size() >= 4) {
                break;
            }

            picked.add(entry);
        }

        picked.sort((a, b) -> Integer.compare(visible.indexOf(a), visible.indexOf(b)));

        List<Evidence> entries = new ArrayList<>();

        for (LogEntry entry : picked) {
            entries.add(entry.evidence());
            player.evidence().put(entry.evidence().id(), entry.evidence());
        }

        player.startCooldown(ActionType.ANALYZE, COOLDOWNS.get(ActionType.ANALYZE));

        connections.sendTo(player.clientId(), Protocol.message(
                MessageType.EVIDENCE_RESULT,
                "server",
                new EvidenceResult(
                        system.id(),
                        entries,
                        "Analizzate " + visible.size() + " voci di log su "
                                + system.name() + ": " + entries.size() + " estratte."
                )
        ));

        reply(player, ActionType.ANALYZE, true, "Analisi di " + system.name() + " completata.");
    }

    private void isolate(PlayerState player, SystemState system) {
        if (system.inMaintenance()) {
            reply(player, ActionType.ISOLATE, false, system.name() + " è in manutenzione.");
            return;
        }

        if (system.isIsolated()) {
            player.startCooldown(ActionType.ISOLATE, 10);
            reply(player, ActionType.ISOLATE, false, system.name() + " è già isolato.");
            return;
        }

        system.isolate(player.clientId(), ISOLATION_SECONDS);
        player.startCooldown(ActionType.ISOLATE, COOLDOWNS.get(ActionType.ISOLATE));

        sendEvent(new GameEvent(
                "ISOLATION",
                "Sistema isolato",
                player.name() + " ha isolato " + system.name()
                        + " per " + ISOLATION_SECONDS + " secondi.",
                system.id(),
                "INFO"
        ), p -> !p.isInsider() || p == player);

        if (player.isInsider()) {
            insiderLog.add(clock() + "  Isolamento di copertura su " + system.name());
        }

        reply(player, ActionType.ISOLATE, true, system.name() + " isolato per " + ISOLATION_SECONDS + " s.");
    }

    private void exfiltrate(PlayerState player, SystemState system) {
        if (system.inMaintenance()) {
            reply(player, ActionType.EXFILTRATE, false, system.name() + " è in manutenzione.");
            return;
        }

        player.startCooldown(ActionType.EXFILTRATE, COOLDOWNS.get(ActionType.EXFILTRATE));

        if (system.isIsolated()) {
            boolean identified = random.nextDouble() < 0.7;

            system.append(new LogEntry(newEvidence(
                    system,
                    "HIGH",
                    "Connessione in uscita bloccata dall'isolamento: richiesta da "
                            + (identified ? player.workstation() : "host interno non identificato"),
                    identified ? player.name() : null
            ), LogEntry.Kind.TRACE));

            system.raiseAlert(6);
            insiderLog.add(clock() + "  Esfiltrazione bloccata su " + system.name() + " (isolato)");

            sendEvent(new GameEvent(
                    "SIEM_ALERT",
                    "Esfiltrazione bloccata",
                    "L'isolamento di " + system.name() + " ha fermato un trasferimento sospetto.",
                    system.id(),
                    "CRITICAL"
            ), p -> true);

            reply(player, ActionType.EXFILTRATE, false,
                    "Connessione bloccata: " + system.name() + " è isolato. Potresti aver lasciato una traccia.");
            return;
        }

        if (system.isHoneypot()) {
            system.append(new LogEntry(newEvidence(
                    system,
                    "CRITICAL",
                    "HONEYPOT: dati esca prelevati da " + player.workstation()
                            + " (account " + player.account() + ")",
                    player.name()
            ), LogEntry.Kind.TRACE));

            system.raiseAlert(10);
            insiderLog.add(clock() + "  Caduto nell'honeypot di " + system.name());

            sendEvent(new GameEvent(
                    "SIEM_ALERT",
                    "Honeypot attivato",
                    "Qualcuno ha prelevato i dati esca da " + system.name() + ". Analizzate i log!",
                    system.id(),
                    "CRITICAL"
            ), p -> true);

            reply(player, ActionType.EXFILTRATE, false,
                    "I dati di " + system.name() + " erano un'esca: honeypot! Sei stato registrato.");
            return;
        }

        exfiltration = Math.min(100, exfiltration + EXFILTRATION_STEP);
        lastExfilSystem = system.id();

        boolean identified = random.nextDouble() < 0.5;

        system.append(new LogEntry(newEvidence(
                system,
                "HIGH",
                Scenario.exfiltrationText(random,
                        identified ? player.workstation() : "host interno non identificato"),
                identified ? player.name() : null
        ), LogEntry.Kind.TRACE));

        insiderLog.add(clock() + "  Esfiltrazione da " + system.name()
                + " (" + exfiltration + "%)" + (identified ? " - identità registrata" : ""));

        if (random.nextDouble() < 0.45) {
            system.raiseAlert(8);

            sendEvent(new GameEvent(
                    "SIEM_ALERT",
                    "Traffico anomalo",
                    "Il SIEM segnala un picco di traffico in uscita da " + system.name() + ".",
                    system.id(),
                    "WARN"
            ), p -> true);
        }

        reply(player, ActionType.EXFILTRATE, true,
                "Esfiltrazione riuscita da " + system.name() + ": " + exfiltration + "% dei dati.");

        ServerLog.game("Esfiltrazione: " + exfiltration + "% (" + system.name() + ")");

        if (exfiltration >= 100) {
            finish(Role.INSIDER,
                    "Esfiltrazione completata: l'insider ha portato fuori tutti i dati.",
                    null,
                    Map.of());
        }
    }

    private void wipe(PlayerState player, SystemState system) {
        if (system.inMaintenance()) {
            reply(player, ActionType.WIPE, false, system.name() + " è in manutenzione.");
            return;
        }

        LogEntry target = null;

        for (LogEntry entry : system.log()) {
            if (entry.kind() == LogEntry.Kind.TRACE && !entry.isWiped()) {
                target = entry;
            }
        }

        if (target == null) {
            player.startCooldown(ActionType.WIPE, 5);
            reply(player, ActionType.WIPE, false, "Nessuna tua traccia da cancellare su " + system.name() + ".");
            return;
        }

        target.setWiped(true);
        player.startCooldown(ActionType.WIPE, COOLDOWNS.get(ActionType.WIPE));

        boolean identified = random.nextDouble() < 0.3;

        system.append(new LogEntry(newEvidence(
                system,
                "MEDIUM",
                "Rotazione manuale dei log di sistema"
                        + (identified ? " eseguita da " + player.workstation() : ""),
                identified ? player.name() : null
        ), LogEntry.Kind.WIPE_MARK));

        insiderLog.add(clock() + "  Tracce cancellate su " + system.name());

        reply(player, ActionType.WIPE, true, "Traccia cancellata dai log di " + system.name() + ".");
    }

    private void frame(PlayerState player, SystemState system, String subject) {
        if (system.inMaintenance()) {
            reply(player, ActionType.FRAME, false, system.name() + " è in manutenzione.");
            return;
        }

        PlayerState victim = null;

        for (PlayerState other : players.values()) {
            if (other != player && other.isConnected() && other.name().equals(subject)) {
                victim = other;
            }
        }

        if (victim == null) {
            reply(player, ActionType.FRAME, false, "Scegli un operatore da incolpare.");
            return;
        }

        system.append(new LogEntry(newEvidence(
                system,
                "HIGH",
                Scenario.exfiltrationText(random, victim.workstation()),
                victim.name()
        ), LogEntry.Kind.FAKE));

        player.startCooldown(ActionType.FRAME, COOLDOWNS.get(ActionType.FRAME));
        insiderLog.add(clock() + "  Prova falsa contro " + victim.name() + " su " + system.name());

        reply(player, ActionType.FRAME, true,
                "Prova falsa contro " + victim.name() + " piazzata su " + system.name() + ".");
    }

    public synchronized void handleShare(String clientId, String evidenceId) {
        PlayerState player = players.get(clientId);

        if (player == null || phase == Phase.LOBBY || phase == Phase.RESULTS) {
            return;
        }

        Evidence evidence = player.evidence().get(evidenceId);

        if (evidence == null) {
            reply(player, null, false, "Puoi condividere solo prove che hai raccolto.");
            return;
        }

        if (!sharedEvidence.add(evidenceId)) {
            reply(player, null, false, "Questa prova è già stata condivisa.");
            return;
        }

        broadcastToPlayers(Protocol.message(
                MessageType.CHAT,
                "server",
                new ChatMessage(player.name(), "ha condiviso una prova verificata", evidence, clock())
        ));
    }

    public synchronized void handleChat(String clientId, String text) {
        PlayerState player = players.get(clientId);

        if (player == null || phase == Phase.LOBBY || text == null) {
            return;
        }

        String clean = text.strip();

        if (clean.isEmpty()) {
            return;
        }

        if (clean.length() > 200) {
            clean = clean.substring(0, 200);
        }

        long now = System.currentTimeMillis();

        if (now - player.lastChat() < 500) {
            return;
        }

        player.setLastChat(now);

        broadcastToPlayers(Protocol.message(
                MessageType.CHAT,
                "server",
                new ChatMessage(player.name(), clean, null, clock())
        ));
    }

    public synchronized void handleVote(String clientId, String target) {
        PlayerState player = players.get(clientId);

        if (player == null || phase != Phase.VOTING || !player.isConnected()) {
            return;
        }

        String vote = target == null ? "" : target.strip();

        if (!vote.isEmpty()) {
            boolean valid = false;

            for (PlayerState other : players.values()) {
                if (other.isConnected() && other.name().equals(vote) && other != player) {
                    valid = true;
                }
            }

            if (!valid) {
                reply(player, null, false, "Voto non valido.");
                return;
            }
        }

        player.setVote(vote);
        ServerLog.game(player.name() + " ha votato.");

        broadcastState();
        notifyListeners();
    }

    public synchronized void playerLeft(String clientId) {
        PlayerState player = players.get(clientId);

        if (player == null || phase == Phase.LOBBY) {
            return;
        }

        player.setConnected(false);
        ServerLog.game(player.name() + " ha lasciato la partita.");

        if (player.isInsider()) {
            finish(Role.ANALYST, "L'insider " + player.name() + " ha abbandonato la sessione.", null, Map.of());
            return;
        }

        int analysts = 0;

        for (PlayerState other : players.values()) {
            if (other.isConnected() && !other.isInsider()) {
                analysts++;
            }
        }

        if (analysts == 0) {
            finish(Role.INSIDER, "Tutti gli analisti hanno lasciato la sessione.", null, Map.of());
            return;
        }

        sendEvent(new GameEvent(
                "INFO",
                "Operatore disconnesso",
                player.name() + " ha lasciato la sala operativa.",
                null,
                "WARN"
        ), p -> true);

        broadcastState();
    }

    private void triggerRandomEvent() {
        List<String> pool = new ArrayList<>(List.of(
                "SIEM_REPORT", "SIEM_REPORT", "BACKUP", "TRAFFIC",
                "MAINTENANCE", "HONEYPOT", "HONEYPOT"
        ));

        if (!badgeAuditDone) {
            pool.add("BADGE");
            pool.add("BADGE");
        }

        pool.removeIf(kind -> kind.equals(lastEventKind));

        String kind = pool.get(random.nextInt(pool.size()));
        lastEventKind = kind;

        switch (kind) {
            case "SIEM_REPORT" -> siemReport();
            case "BACKUP" -> backupRestore();
            case "TRAFFIC" -> trafficSpike();
            case "BADGE" -> badgeAudit();
            case "MAINTENANCE" -> maintenance();
            default -> honeypot();
        }

        ServerLog.game("Evento: " + kind);
    }

    private void siemReport() {
        SystemState system = lastExfilSystem == null ? null : systems.get(lastExfilSystem);

        if (system == null) {
            sendEvent(new GameEvent(
                    "SIEM_REPORT",
                    "Report SIEM",
                    "Nessun trasferimento anomalo confermato finora. Restate all'erta.",
                    null,
                    "INFO"
            ), p -> true);
            return;
        }

        system.raiseAlert(8);

        sendEvent(new GameEvent(
                "SIEM_REPORT",
                "Report SIEM",
                "L'ultimo trasferimento sospetto è partito da " + system.name() + ".",
                system.id(),
                "WARN"
        ), p -> true);
    }

    private void backupRestore() {
        List<SystemState> candidates = new ArrayList<>();

        for (SystemState system : systems.values()) {
            for (LogEntry entry : system.log()) {
                if (entry.isWiped()) {
                    candidates.add(system);
                    break;
                }
            }
        }

        if (candidates.isEmpty()) {
            SystemState system = randomSystem();

            sendEvent(new GameEvent(
                    "BACKUP",
                    "Backup verificato",
                    "Il backup di " + system.name() + " è integro: nessuna voce mancante.",
                    system.id(),
                    "INFO"
            ), p -> true);
            return;
        }

        SystemState system = candidates.get(random.nextInt(candidates.size()));
        int restored = 0;

        for (LogEntry entry : system.log()) {
            if (entry.isWiped()) {
                entry.setWiped(false);
                restored++;
            }
        }

        sendEvent(new GameEvent(
                "BACKUP",
                "Log ripristinati",
                "Il backup notturno ha recuperato " + restored
                        + (restored == 1 ? " voce cancellata" : " voci cancellate")
                        + " su " + system.name() + ".",
                system.id(),
                "SUCCESS"
        ), p -> true);
    }

    private void trafficSpike() {
        for (SystemState system : systems.values()) {
            for (int i = 0; i < 3; i++) {
                addNoise(system);
            }
        }

        sendEvent(new GameEvent(
                "TRAFFIC",
                "Picco di traffico",
                "Un aumento improvviso di attività ha riempito i log di rumore.",
                null,
                "INFO"
        ), p -> true);
    }

    private void badgeAudit() {
        PlayerState insider = insider();
        List<PlayerState> others = new ArrayList<>();

        for (PlayerState player : players.values()) {
            if (player.isConnected() && player != insider) {
                others.add(player);
            }
        }

        if (insider == null || others.isEmpty()) {
            siemReport();
            return;
        }

        badgeAuditDone = true;

        List<String> names = new ArrayList<>(List.of(
                insider.name(),
                others.get(random.nextInt(others.size())).name()
        ));

        Collections.shuffle(names, random);

        sendEvent(new GameEvent(
                "BADGE",
                "Registro badge",
                names.get(0) + " e " + names.get(1)
                        + " sono entrati nella sala server alle " + clock().substring(0, 5) + ".",
                null,
                "WARN"
        ), p -> true);
    }

    private void maintenance() {
        List<SystemState> candidates = new ArrayList<>();

        for (SystemState system : systems.values()) {
            if (!system.inMaintenance()) {
                candidates.add(system);
            }
        }

        SystemState system = candidates.get(random.nextInt(candidates.size()));
        system.setMaintenance(MAINTENANCE_SECONDS);

        sendEvent(new GameEvent(
                "MAINTENANCE",
                "Manutenzione programmata",
                system.name() + " non sarà disponibile per " + MAINTENANCE_SECONDS + " secondi.",
                system.id(),
                "WARN"
        ), p -> true);
    }

    private void honeypot() {
        List<SystemState> candidates = new ArrayList<>();

        for (SystemState system : systems.values()) {
            if (!system.inMaintenance() && !system.isIsolated() && !system.isHoneypot()) {
                candidates.add(system);
            }
        }

        if (candidates.isEmpty()) {
            trafficSpike();
            return;
        }

        SystemState system = candidates.get(random.nextInt(candidates.size()));
        system.setHoneypot(HONEYPOT_SECONDS);

        sendEvent(new GameEvent(
                "HONEYPOT",
                "Honeypot attivo",
                "Dati esca piazzati su " + system.name() + " per " + HONEYPOT_SECONDS
                        + " secondi. Non dirlo in chat: l'insider potrebbe leggere.",
                system.id(),
                "SUCCESS"
        ), p -> !p.isInsider());
    }

    private void addNoise(SystemState system) {
        List<PlayerState> list = new ArrayList<>(players.values());
        PlayerState player = list.get(random.nextInt(list.size()));

        Scenario.Noise noise = Scenario.noise(random, system, player);

        system.append(new LogEntry(newEvidence(
                system,
                noise.severity(),
                noise.text(),
                noise.origin() == null ? null : noise.origin().name()
        ), LogEntry.Kind.NOISE));
    }

    private Evidence newEvidence(SystemState system, String severity, String text, String origin) {
        evidenceCounter++;

        return new Evidence(
                String.format("EV-%04d", evidenceCounter),
                system.id(),
                clock(),
                severity,
                text,
                origin
        );
    }

    private SystemState randomSystem() {
        List<SystemState> list = new ArrayList<>(systems.values());
        return list.get(random.nextInt(list.size()));
    }

    private PlayerState insider() {
        for (PlayerState player : players.values()) {
            if (player.isInsider()) {
                return player;
            }
        }

        return null;
    }

    private String clock() {
        int value = clock % (24 * 3600);
        return String.format("%02d:%02d:%02d", value / 3600, (value / 60) % 60, value % 60);
    }

    private static String formatDuration(int seconds) {
        int minutes = seconds / 60;
        int rest = seconds % 60;

        if (minutes == 0) {
            return rest + " secondi";
        }

        if (rest == 0) {
            return minutes + (minutes == 1 ? " minuto" : " minuti");
        }

        return minutes + ":" + String.format("%02d", rest) + " minuti";
    }

    private GameState stateFor(PlayerState player) {
        List<SystemNode> nodes = new ArrayList<>();

        for (SystemState system : systems.values()) {
            SystemStatus status = SystemStatus.ONLINE;

            if (system.inMaintenance()) {
                status = SystemStatus.MAINTENANCE;
            } else if (system.isIsolated()
                    && (!player.isInsider() || player.clientId().equals(system.isolatedBy()))) {
                status = SystemStatus.ISOLATED;
            } else if (system.isHoneypot() && !player.isInsider()) {
                status = SystemStatus.HONEYPOT;
            }

            nodes.add(new SystemNode(
                    system.id(),
                    system.name(),
                    system.description(),
                    status,
                    system.hasAlert(),
                    system.visibleLog().size()
            ));
        }

        List<PlayerInfo> roster = new ArrayList<>();

        for (PlayerState other : players.values()) {
            roster.add(new PlayerInfo(
                    other.name(),
                    other.workstation(),
                    other.vote() != null,
                    other.isConnected()
            ));
        }

        return new GameState(
                phase,
                Math.max(0, secondsLeft),
                phaseDuration,
                exfiltration,
                nodes,
                roster,
                player.cooldowns()
        );
    }

    private void sendState(PlayerState player) {
        if (player.isConnected()) {
            connections.sendTo(player.clientId(), Protocol.message(
                    MessageType.GAME_UPDATE, "server", stateFor(player)
            ));
        }
    }

    private void broadcastState() {
        for (PlayerState player : players.values()) {
            sendState(player);
        }
    }

    private void broadcastToPlayers(Message message) {
        for (PlayerState player : players.values()) {
            if (player.isConnected()) {
                connections.sendTo(player.clientId(), message);
            }
        }
    }

    private void sendEvent(GameEvent event, Predicate<PlayerState> filter) {
        Message message = Protocol.message(MessageType.GAME_EVENT, "server", event);

        for (PlayerState player : players.values()) {
            if (player.isConnected() && filter.test(player)) {
                connections.sendTo(player.clientId(), message);
            }
        }
    }

    private void reply(PlayerState player, ActionType action, boolean success, String text) {
        connections.sendTo(player.clientId(), Protocol.message(
                MessageType.ACTION_RESULT,
                "server",
                new ActionResult(
                        action,
                        success,
                        text,
                        action == null ? 0 : player.cooldown(action)
                )
        ));
    }
}
