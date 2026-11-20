package it.soc.server.game;

import it.soc.common.Message;
import it.soc.common.MessageType;
import it.soc.common.Protocol;
import it.soc.common.model.ActionType;
import it.soc.common.model.GameEvent;
import it.soc.common.model.GameResult;
import it.soc.common.model.LobbyState;
import it.soc.common.model.Phase;
import it.soc.common.model.PlayerAction;
import it.soc.common.model.Role;
import it.soc.server.ConnectionManager;
import it.soc.server.GameConfig;
import it.soc.server.ServerLog;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class GameManager {

    public static final String STATS_FILE = "statistiche.properties";

    private static final int FORENSIC_MIN_PLAYERS = 5;

    private final ConnectionManager connections;
    private final GameConfig config;
    private final GameStats stats;
    private final GameSession session;
    private final GameMessenger messenger;
    private final PlayerActions actions;
    private final VotingBooth voting;
    private final TeamChannel channel;
    private final EventDirector events;
    private final boolean autoTick;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "SOC-Game-Ticker");
                thread.setDaemon(true);
                return thread;
            });

    private ScheduledFuture<?> tickTask;

    public GameManager(ConnectionManager connections, GameConfig config) {
        this(connections, config, new GameStats(Path.of(STATS_FILE)), true, new Random());
    }

    GameManager(ConnectionManager connections, GameConfig config, GameStats stats,
                boolean autoTick, Random random) {
        this.connections = connections;
        this.config = config;
        this.stats = stats;
        this.autoTick = autoTick;
        this.session = new GameSession(random);
        this.messenger = new GameMessenger(connections, session, config);
        this.actions = new PlayerActions(session, messenger, config, this::finish);
        this.voting = new VotingBooth(session, messenger, this::finish);
        this.channel = new TeamChannel(session, messenger);
        this.events = new EventDirector(session, messenger);
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
        return session.phase;
    }

    public synchronized boolean isInGame() {
        return session.phase != Phase.LOBBY;
    }

    public synchronized boolean isPlaying(String clientId) {
        return session.phase != Phase.LOBBY && session.players.containsKey(clientId);
    }

    public synchronized GameResult lastResult() {
        return session.lastResult;
    }

    public synchronized LobbyState lobbyState() {
        return new LobbyState(
                connections.getPlayerNames(),
                connections.getHostName(),
                config.minPlayers(),
                session.phase != Phase.LOBBY
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

        for (PlayerState player : session.players.values()) {
            list.add(new AdminSnapshot.Player(
                    player.name(),
                    player.workstation(),
                    player.role(),
                    player.isActive(),
                    player.vote() != null,
                    player.actions(),
                    player.evidence().size()
            ));
        }

        return new AdminSnapshot(
                session.phase,
                session.secondsLeft,
                session.phaseDuration,
                session.exfiltration,
                connections.getPlayerNames(),
                connections.getHostName(),
                list
        );
    }

    public synchronized String startGame(String requesterId) {
        if (session.phase != Phase.LOBBY) {
            return "Una partita è già in corso.";
        }

        if (requesterId != null && !requesterId.equals(connections.getHostId())) {
            return "Solo l'host può avviare la sessione.";
        }

        List<String> ids = connections.getLobbyClientIds();

        if (ids.size() < config.minPlayers()) {
            return "Servono almeno " + config.minPlayers() + " operatori per iniziare.";
        }

        session.reset();

        List<Integer> stations = new ArrayList<>();

        for (int i = 1; i <= 24; i++) {
            stations.add(i);
        }

        Collections.shuffle(stations, session.random);

        String insiderId = ids.get(session.random.nextInt(ids.size()));
        String forensicId = null;

        if (ids.size() >= FORENSIC_MIN_PLAYERS) {
            List<String> others = new ArrayList<>(ids);
            others.remove(insiderId);
            forensicId = others.get(session.random.nextInt(others.size()));
        }

        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            Role role = id.equals(insiderId) ? Role.INSIDER
                    : id.equals(forensicId) ? Role.FORENSIC : Role.ANALYST;

            session.players.put(id, new PlayerState(
                    id,
                    connections.getPlayerName(id),
                    String.format("WS-%02d", stations.get(i)),
                    role
            ));
        }

        for (SystemState system : Scenario.createSystems()) {
            session.systems.put(system.id(), system);
        }

        session.clock = 2 * 3600 + 38 * 60;

        for (SystemState system : session.systems.values()) {
            for (int i = 0; i < 5; i++) {
                session.addNoise(system);
                session.clock += 11;
            }
        }

        session.phase = Phase.BRIEFING;
        session.phaseDuration = config.briefingSeconds();
        session.secondsLeft = session.phaseDuration;

        for (PlayerState player : session.players.values()) {
            connections.sendTo(player.clientId(), Protocol.message(
                    MessageType.ROLE_ASSIGN,
                    "server",
                    Scenario.roleInfo(player.role(), player.workstation())
            ));
        }

        for (PlayerState player : session.players.values()) {
            connections.sendTo(player.clientId(), Protocol.message(
                    MessageType.GAME_START, "server", messenger.stateFor(player)
            ));
        }

        if (autoTick) {
            tickTask = scheduler.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
        }

        ServerLog.game("Partita avviata con " + session.players.size()
                + " operatori (" + config.difficulty().label() + "). Insider: "
                + session.players.get(insiderId).name());

        connections.broadcast(Protocol.message(
                MessageType.LOBBY_UPDATE, "server", lobbyState()
        ));

        notifyListeners();
        return null;
    }

    public synchronized void stopGame() {
        if (session.phase == Phase.LOBBY) {
            return;
        }

        finish(null, "Sessione interrotta dall'amministratore.", null, Map.of());
    }

    public void shutdown() {
        scheduler.shutdownNow();
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

    synchronized void tickOnce() {
        doTick();
    }

    synchronized void setEventsEnabled(boolean enabled) {
        session.eventsEnabled = enabled;
    }

    synchronized PlayerState playerNamed(String name) {
        return session.byName(name);
    }

    synchronized GameSession session() {
        return session;
    }

    private void doTick() {
        if (session.phase == Phase.LOBBY) {
            return;
        }

        session.secondsLeft--;
        session.clock += 7;

        for (PlayerState player : session.players.values()) {
            player.tickCooldowns();
        }

        if (!tickDisconnections()) {
            return;
        }

        if (session.phase == Phase.INVESTIGATION) {
            session.elapsed++;

            if (session.emergencyCooldown > 0) {
                session.emergencyCooldown--;
            }

            for (SystemState system : session.systems.values()) {
                system.tick();
            }

            if (session.elapsed % 4 == 0) {
                session.addNoise(session.randomSystem());
            }

            if (session.eventsEnabled && session.elapsed >= session.nextEventAt
                    && session.secondsLeft > 10) {
                events.trigger();
                session.nextEventAt = session.elapsed + events.nextDelay();
            }
        }

        if (session.phase == Phase.VOTING && voting.allVoted() && session.secondsLeft > 3) {
            session.secondsLeft = 3;
        }

        if (session.secondsLeft <= 0) {
            advancePhase();
        }

        if (session.phase != Phase.LOBBY) {
            messenger.broadcastState();
        }
    }

    private boolean tickDisconnections() {
        List<PlayerState> expired = new ArrayList<>();

        for (PlayerState player : session.players.values()) {
            if (!player.isConnected() && !player.isExpelled()) {
                player.tickDisconnected();

                if (player.disconnectedFor() > GameSession.RECONNECT_GRACE) {
                    expired.add(player);
                }
            }
        }

        for (PlayerState player : expired) {
            player.setExpelled(true);
            ServerLog.game(player.name() + " non è rientrato in tempo.");

            if (player.isInsider()) {
                finish(Role.ANALYST,
                        "L'insider " + player.name() + " ha abbandonato la sessione.",
                        null, Map.of());
                return false;
            }

            messenger.sendEvent("INFO", "Operatore perso",
                    player.name() + " non è rientrato: la sua postazione è fuori dalla sessione.",
                    null, "WARN");
        }

        if (!expired.isEmpty() && session.activeAnalysts() == 0) {
            finish(Role.INSIDER, "Tutti gli analisti hanno lasciato la sessione.", null, Map.of());
            return false;
        }

        return true;
    }

    private void advancePhase() {
        switch (session.phase) {
            case BRIEFING -> {
                session.phase = Phase.INVESTIGATION;
                session.phaseDuration = config.investigationSeconds();
                session.secondsLeft = session.phaseDuration;

                messenger.sendEvent(new GameEvent(
                        "PHASE",
                        "Indagine avviata",
                        "Avete " + GameSession.formatDuration(session.phaseDuration)
                                + " per smascherare l'insider.",
                        null,
                        "INFO"
                ), p -> true);

                ServerLog.game("Fase di indagine iniziata.");
            }
            case INVESTIGATION -> {
                session.phase = Phase.VOTING;
                session.phaseDuration = config.votingSeconds();
                session.secondsLeft = session.phaseDuration;
                session.callRequests.clear();

                for (PlayerState player : session.players.values()) {
                    player.setVote(null);
                }

                messenger.sendEvent(new GameEvent(
                        "PHASE",
                        "Fase di voto",
                        "Discutete e votate l'operatore sospetto entro "
                                + session.phaseDuration + " secondi.",
                        null,
                        "WARN"
                ), p -> true);

                ServerLog.game("Fase di voto iniziata.");
            }
            case VOTING -> {
                if (session.emergency) {
                    voting.resolveEmergency();
                } else {
                    voting.finishFinal();
                }
            }
            default -> {
            }
        }
    }

    private void finish(Role winner, String reason, String ejected, Map<String, Integer> votes) {
        PlayerState insider = session.insider();

        int collected = 0;

        for (PlayerState player : session.players.values()) {
            collected += player.evidence().size();
        }

        Map<String, Integer> scores = new LinkedHashMap<>();
        Map<String, String> records = new LinkedHashMap<>();

        if (winner != null) {
            for (PlayerState player : session.players.values()) {
                boolean won = (winner == Role.INSIDER) == player.isInsider();
                int points = points(player, winner, insider, ejected);

                scores.put(player.name(), points);

                if (session.players.size() >= 2) {
                    stats.record(player.name(), won, points);
                }
            }

            stats.save();

            for (PlayerState player : session.players.values()) {
                records.put(player.name(), stats.summary(player.name()));
            }
        }

        GameResult result = new GameResult(
                winner,
                reason,
                insider == null ? null : insider.name(),
                ejected,
                votes,
                session.exfiltration,
                collected,
                session.sharedEvidence.size(),
                List.copyOf(session.insiderLog),
                scores,
                records
        );

        session.lastResult = result;

        Message message = Protocol.message(MessageType.GAME_OVER, "server", result);

        for (PlayerState player : session.players.values()) {
            if (player.isConnected()) {
                connections.sendTo(player.clientId(), message);
            }
        }

        if (tickTask != null) {
            tickTask.cancel(false);
            tickTask = null;
        }

        session.phase = Phase.LOBBY;
        session.secondsLeft = 0;
        session.emergency = false;
        session.players.clear();

        ServerLog.game("Partita terminata. Vincitore: "
                + (winner == null ? "nessuno" : winner) + " - " + reason);

        connections.broadcast(Protocol.message(
                MessageType.LOBBY_UPDATE, "server", lobbyState()
        ));
    }

    private int points(PlayerState player, Role winner, PlayerState insider, String ejected) {
        int points = 0;
        boolean won = (winner == Role.INSIDER) == player.isInsider();

        if (won) {
            points += 100;
        }

        if (player.isInsider()) {
            points += session.exfiltration / 2;
            points += 20 * Objectives.completed(player);

            if (ejected == null || !ejected.equals(player.name())) {
                points += 20;
            }

            return points;
        }

        points += 25 * Objectives.completed(player);

        if (insider != null && insider.name().equals(player.vote())) {
            points += 25;
        }

        return points;
    }

    public synchronized void handleAction(String clientId, PlayerAction action) {
        actions.handle(clientId, action);
        notifyListeners();
    }

    public synchronized void handleEvidenceRequest(String clientId, String systemId) {
        handleAction(clientId, new PlayerAction(ActionType.ANALYZE, systemId, null));
    }

    public synchronized void handleShare(String clientId, String evidenceId) {
        channel.share(clientId, evidenceId);
    }

    public synchronized void handleChat(String clientId, String text) {
        channel.chat(clientId, text);
    }

    public synchronized void handleVote(String clientId, String target) {
        voting.handleVote(clientId, target);
        notifyListeners();
    }

    public synchronized void handleCallVote(String clientId) {
        voting.callVote(clientId);
        notifyListeners();
    }

    public synchronized void playerLeft(String clientId) {
        PlayerState player = session.players.get(clientId);

        if (player == null || session.phase == Phase.LOBBY) {
            return;
        }

        player.setConnected(false);
        session.callRequests.remove(clientId);
        ServerLog.game(player.name() + " ha perso la connessione.");

        if (session.connectedCount() == 0) {
            finish(null, "Tutti gli operatori hanno lasciato la sessione.", null, Map.of());
            return;
        }

        messenger.sendEvent("INFO", "Operatore disconnesso",
                player.name() + " ha perso la connessione e ha " + GameSession.RECONNECT_GRACE
                        + " secondi per rientrare con lo stesso nickname.",
                null, "WARN");

        messenger.broadcastState();
    }

    public synchronized boolean canRejoin(String name) {
        return findRejoinable(name) != null;
    }

    private PlayerState findRejoinable(String name) {
        if (session.phase == Phase.LOBBY || name == null) {
            return null;
        }

        for (PlayerState player : session.players.values()) {
            if (!player.isConnected() && !player.isExpelled()
                    && player.name().equalsIgnoreCase(name.strip())) {
                return player;
            }
        }

        return null;
    }

    public synchronized boolean rejoin(String clientId, String name) {
        PlayerState player = findRejoinable(name);

        if (player == null) {
            return false;
        }

        session.rekey(player.clientId(), player, clientId);
        player.setClientId(clientId);
        player.setConnected(true);

        connections.sendTo(clientId, Protocol.message(
                MessageType.LOBBY_UPDATE, "server", lobbyState()
        ));

        connections.sendTo(clientId, Protocol.message(
                MessageType.ROLE_ASSIGN,
                "server",
                Scenario.roleInfo(player.role(), player.workstation())
        ));

        connections.sendTo(clientId, Protocol.message(
                MessageType.GAME_START, "server", messenger.stateFor(player)
        ));

        messenger.sendEvent(new GameEvent(
                "INFO",
                "Operatore rientrato",
                player.name() + " è tornato nella sala operativa.",
                null,
                "SUCCESS"
        ), p -> p != player);

        ServerLog.game(player.name() + " è rientrato nella partita.");

        messenger.broadcastState();
        notifyListeners();
        return true;
    }
}
