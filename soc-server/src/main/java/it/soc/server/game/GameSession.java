package it.soc.server.game;

import it.soc.common.model.Evidence;
import it.soc.common.model.GameResult;
import it.soc.common.model.Phase;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

class GameSession {

    static final int EMERGENCY_MIN_ELAPSED = 45;
    static final int EMERGENCY_VOTE_SECONDS = 40;
    static final int EMERGENCY_COOLDOWN = 60;
    static final int EMERGENCY_MAX = 2;
    static final int EMERGENCY_PENALTY = 10;
    static final int RECONNECT_GRACE = 45;

    final Map<String, PlayerState> players = new LinkedHashMap<>();
    final Map<String, SystemState> systems = new LinkedHashMap<>();
    final List<String> insiderLog = new ArrayList<>();
    final Set<String> sharedEvidence = new HashSet<>();
    final Set<String> callRequests = new LinkedHashSet<>();
    final Random random;

    Phase phase = Phase.LOBBY;
    int secondsLeft;
    int phaseDuration;
    int exfiltration;
    int blocked;
    int evidenceCounter;
    int clock;
    int elapsed;
    int nextEventAt;
    String lastEventKind;
    boolean badgeAuditDone;
    String lastExfilSystem;
    boolean eventsEnabled = true;
    boolean emergency;
    int emergencyCalls;
    int emergencyCooldown;
    int savedSecondsLeft;
    int savedDuration;
    GameResult lastResult;

    GameSession(Random random) {
        this.random = random;
    }

    void reset() {
        players.clear();
        systems.clear();
        insiderLog.clear();
        sharedEvidence.clear();
        callRequests.clear();
        exfiltration = 0;
        blocked = 0;
        evidenceCounter = 0;
        elapsed = 0;
        nextEventAt = 30;
        lastEventKind = null;
        badgeAuditDone = false;
        lastExfilSystem = null;
        emergency = false;
        emergencyCalls = 0;
        emergencyCooldown = 0;
        savedSecondsLeft = 0;
        savedDuration = 0;
    }

    PlayerState insider() {
        for (PlayerState player : players.values()) {
            if (player.isInsider()) {
                return player;
            }
        }

        return null;
    }

    PlayerState byName(String name) {
        for (PlayerState player : players.values()) {
            if (player.name().equals(name)) {
                return player;
            }
        }

        return null;
    }

    int activeCount() {
        int count = 0;

        for (PlayerState player : players.values()) {
            if (player.isActive()) {
                count++;
            }
        }

        return count;
    }

    int activeAnalysts() {
        int count = 0;

        for (PlayerState player : players.values()) {
            if (player.isActive() && !player.isInsider()) {
                count++;
            }
        }

        return count;
    }

    int connectedCount() {
        int count = 0;

        for (PlayerState player : players.values()) {
            if (player.isConnected()) {
                count++;
            }
        }

        return count;
    }

    int requiredCalls() {
        return Math.max(1, (activeCount() + 1) / 2);
    }

    SystemState randomSystem() {
        List<SystemState> list = new ArrayList<>(systems.values());
        return list.get(random.nextInt(list.size()));
    }

    String clock() {
        int value = clock % (24 * 3600);
        return String.format("%02d:%02d:%02d", value / 3600, (value / 60) % 60, value % 60);
    }

    Evidence newEvidence(SystemState system, String severity, String text, String origin) {
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

    void addNoise(SystemState system) {
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

    void rekey(String oldId, PlayerState player, String newId) {
        Map<String, PlayerState> copy = new LinkedHashMap<>();

        for (var entry : players.entrySet()) {
            if (entry.getKey().equals(oldId)) {
                copy.put(newId, player);
            } else {
                copy.put(entry.getKey(), entry.getValue());
            }
        }

        players.clear();
        players.putAll(copy);
    }

    static String formatDuration(int seconds) {
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
}
