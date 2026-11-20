package it.soc.server.game;

import it.soc.common.Message;
import it.soc.common.MessageType;
import it.soc.common.Protocol;
import it.soc.common.model.ActionResult;
import it.soc.common.model.ActionType;
import it.soc.common.model.GameEvent;
import it.soc.common.model.GameState;
import it.soc.common.model.Phase;
import it.soc.common.model.PlayerInfo;
import it.soc.common.model.SystemNode;
import it.soc.common.model.SystemStatus;
import it.soc.server.ConnectionManager;
import it.soc.server.GameConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

class GameMessenger {

    private final ConnectionManager connections;
    private final GameSession session;
    private final GameConfig config;

    GameMessenger(ConnectionManager connections, GameSession session, GameConfig config) {
        this.connections = connections;
        this.session = session;
        this.config = config;
    }

    GameState stateFor(PlayerState player) {
        List<SystemNode> nodes = new ArrayList<>();

        for (SystemState system : session.systems.values()) {
            SystemStatus status = SystemStatus.ONLINE;

            if (system.inMaintenance()) {
                status = SystemStatus.MAINTENANCE;
            } else if (system.isJammed()) {
                status = SystemStatus.UNKNOWN;
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

        for (PlayerState other : session.players.values()) {
            roster.add(new PlayerInfo(
                    other.name(),
                    other.workstation(),
                    other.vote() != null,
                    other.isConnected(),
                    other.isExpelled()
            ));
        }

        return new GameState(
                session.phase,
                Math.max(0, session.secondsLeft),
                session.phaseDuration,
                session.exfiltration,
                nodes,
                roster,
                player.cooldowns(),
                session.blocked,
                config.blocksToWin(),
                Objectives.describe(player, session.exfiltration),
                session.emergency,
                session.callRequests.size(),
                session.requiredCalls(),
                canCallVote(player)
        );
    }

    boolean canCallVote(PlayerState player) {
        return session.phase == Phase.INVESTIGATION
                && player.isActive()
                && session.elapsed >= GameSession.EMERGENCY_MIN_ELAPSED
                && session.emergencyCalls < GameSession.EMERGENCY_MAX
                && session.emergencyCooldown == 0
                && !session.callRequests.contains(player.clientId());
    }

    void sendTo(PlayerState player, Message message) {
        if (player.isConnected()) {
            connections.sendTo(player.clientId(), message);
        }
    }

    void sendState(PlayerState player) {
        if (player.isConnected()) {
            connections.sendTo(player.clientId(), Protocol.message(
                    MessageType.GAME_UPDATE, "server", stateFor(player)
            ));
        }
    }

    void broadcastState() {
        for (PlayerState player : session.players.values()) {
            sendState(player);
        }
    }

    void broadcast(Message message) {
        for (PlayerState player : session.players.values()) {
            sendTo(player, message);
        }
    }

    void sendEvent(GameEvent event, Predicate<PlayerState> filter) {
        Message message = Protocol.message(MessageType.GAME_EVENT, "server", event);

        for (PlayerState player : session.players.values()) {
            if (player.isConnected() && filter.test(player)) {
                connections.sendTo(player.clientId(), message);
            }
        }
    }

    void sendEvent(String kind, String title, String text, String system, String severity) {
        sendEvent(new GameEvent(kind, title, text, system, severity), p -> true);
    }

    void reply(PlayerState player, ActionType action, boolean success, String text) {
        sendTo(player, Protocol.message(
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
