package it.soc.server.game;

import static org.junit.jupiter.api.Assertions.assertNull;

import it.soc.common.Message;
import it.soc.common.MessageType;
import it.soc.common.Protocol;
import it.soc.common.model.ActionResult;
import it.soc.common.model.ActionType;
import it.soc.common.model.Phase;
import it.soc.common.model.PlayerAction;
import it.soc.common.model.Role;
import it.soc.common.model.RoleInfo;
import it.soc.server.ConnectionManager;
import it.soc.server.GameConfig;
import it.soc.server.MessageSink;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

class GameFixture {

    static class RecordingSink implements MessageSink {

        final List<Message> messages = new CopyOnWriteArrayList<>();

        @Override
        public void sendMessage(Message message) {
            messages.add(message);
        }

        @Override
        public void close() {
        }

        <T> List<T> all(MessageType type, Class<T> payload) {
            List<T> list = new ArrayList<>();

            for (Message message : messages) {
                if (message.type() == type) {
                    list.add(Protocol.fromContent(message.content(), payload));
                }
            }

            return list;
        }

        <T> T last(MessageType type, Class<T> payload) {
            List<T> list = all(type, payload);
            return list.isEmpty() ? null : list.get(list.size() - 1);
        }

        int count(MessageType type) {
            int count = 0;

            for (Message message : messages) {
                if (message.type() == type) {
                    count++;
                }
            }

            return count;
        }
    }

    static final String[] NAMES = {"Alice", "Bruno", "Carla", "Dario", "Elena", "Fabio"};

    final ConnectionManager connections = new ConnectionManager();
    final GameConfig config = new GameConfig();
    final GameManager game;
    final Map<String, RecordingSink> sinks = new LinkedHashMap<>();
    final Map<String, String> ids = new LinkedHashMap<>();

    GameFixture(int players, int investigationSeconds) {
        config.setBriefingSeconds(3);
        config.setInvestigationSeconds(investigationSeconds);
        config.setVotingSeconds(60);

        game = new GameManager(connections, config, GameStats.inMemory(), false, new Random(11));
        game.setEventsEnabled(false);

        for (int i = 0; i < players; i++) {
            RecordingSink sink = new RecordingSink();
            String id = connections.register(sink);

            connections.setPlayerName(id, NAMES[i]);
            sinks.put(NAMES[i], sink);
            ids.put(NAMES[i], id);
        }
    }

    GameFixture start() {
        assertNull(game.startGame(null));

        for (int i = 0; i < 3; i++) {
            game.tickOnce();
        }

        return this;
    }

    void tick(int seconds) {
        for (int i = 0; i < seconds; i++) {
            game.tickOnce();
        }
    }

    void tickUntil(Phase phase) {
        for (int i = 0; i < 5000 && game.phase() != phase; i++) {
            game.tickOnce();
        }
    }

    void tickUntilReady(String name, ActionType action) {
        for (int i = 0; i < 500 && game.playerNamed(name).cooldown(action) > 0; i++) {
            game.tickOnce();
        }
    }

    void act(String name, ActionType action, String target) {
        game.handleAction(ids.get(name), new PlayerAction(action, target, null));
    }

    Role roleOf(String name) {
        RoleInfo info = sinks.get(name).last(MessageType.ROLE_ASSIGN, RoleInfo.class);
        return info == null ? null : info.role();
    }

    String insider() {
        for (String name : sinks.keySet()) {
            if (roleOf(name) == Role.INSIDER) {
                return name;
            }
        }

        return null;
    }

    List<String> analysts() {
        List<String> list = new ArrayList<>();

        for (String name : sinks.keySet()) {
            if (roleOf(name) != Role.INSIDER) {
                list.add(name);
            }
        }

        return list;
    }

    ActionResult lastResult(String name) {
        return sinks.get(name).last(MessageType.ACTION_RESULT, ActionResult.class);
    }

    void vote(String name, String target) {
        game.handleVote(ids.get(name), target);
    }
}
