package it.soc.server.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.soc.common.MessageType;
import it.soc.common.model.ActionResult;
import it.soc.common.model.ActionType;
import it.soc.common.model.ChatMessage;
import it.soc.common.model.GameResult;
import it.soc.common.model.GameState;
import it.soc.common.model.Phase;
import it.soc.common.model.Role;
import it.soc.server.GameConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class GameRulesTest {

    @Test
    void insiderWinsWhenExfiltrationReachesOneHundred() {
        GameFixture f = new GameFixture(3, 3000).start();
        String insider = f.insider();

        for (int i = 0; i < 10; i++) {
            f.tickUntilReady(insider, ActionType.EXFILTRATE);
            f.act(insider, ActionType.EXFILTRATE, "DB");
        }

        GameResult result = f.game.lastResult();

        assertNotNull(result);
        assertEquals(Role.INSIDER, result.winner());
        assertEquals(100, result.exfiltration());
        assertEquals(Phase.LOBBY, f.game.phase());
    }

    @Test
    void analystsWinWhenEnoughExfiltrationsAreBlocked() {
        GameFixture f = new GameFixture(3, 3000).start();
        String insider = f.insider();
        String analyst = f.analysts().get(0);

        for (int i = 0; i < f.config.blocksToWin(); i++) {
            f.tickUntilReady(analyst, ActionType.ISOLATE);
            f.act(analyst, ActionType.ISOLATE, "DB");
            f.tickUntilReady(insider, ActionType.EXFILTRATE);
            f.act(insider, ActionType.EXFILTRATE, "DB");
        }

        GameResult result = f.game.lastResult();

        assertNotNull(result);
        assertEquals(Role.ANALYST, result.winner());
        assertEquals(0, result.exfiltration());
        assertTrue(result.reason().contains("contenuto"));
    }

    @Test
    void honeypotHitCountsAsBlockedExfiltration() {
        GameFixture f = new GameFixture(3, 3000).start();
        String insider = f.insider();

        f.game.session().systems.get("DB").setHoneypot(60);
        f.tickUntilReady(insider, ActionType.EXFILTRATE);
        f.act(insider, ActionType.EXFILTRATE, "DB");

        assertEquals(0, f.game.session().exfiltration);
        assertEquals(1, f.game.session().blocked);
    }

    @Test
    void majorityOnInsiderGivesAnalystsTheVictory() {
        GameFixture f = new GameFixture(3, 30).start();
        String insider = f.insider();
        List<String> analysts = f.analysts();

        f.tickUntil(Phase.VOTING);

        f.vote(analysts.get(0), insider);
        f.vote(analysts.get(1), insider);
        f.vote(insider, "");
        f.tick(5);

        GameResult result = f.game.lastResult();

        assertEquals(Role.ANALYST, result.winner());
        assertEquals(insider, result.ejected());
        assertEquals(insider, result.insider());
    }

    @Test
    void tiedVoteGivesInsiderTheVictory() {
        GameFixture f = new GameFixture(3, 30).start();
        String insider = f.insider();
        List<String> analysts = f.analysts();

        f.tickUntil(Phase.VOTING);

        f.vote(analysts.get(0), analysts.get(1));
        f.vote(analysts.get(1), analysts.get(0));
        f.vote(insider, "");
        f.tick(5);

        GameResult result = f.game.lastResult();

        assertEquals(Role.INSIDER, result.winner());
        assertNull(result.ejected());
    }

    @Test
    void abstentionsOutweighingVotesExpelNobody() {
        GameFixture f = new GameFixture(3, 30).start();
        String insider = f.insider();
        List<String> analysts = f.analysts();

        f.tickUntil(Phase.VOTING);

        f.vote(analysts.get(0), insider);
        f.vote(analysts.get(1), "");
        f.vote(insider, "");
        f.tick(5);

        assertEquals(Role.INSIDER, f.game.lastResult().winner());
        assertNull(f.game.lastResult().ejected());
    }

    @Test
    void actionsInCooldownAreRejected() {
        GameFixture f = new GameFixture(3, 3000).start();
        String analyst = f.analysts().get(0);

        f.act(analyst, ActionType.ANALYZE, "DB");
        assertTrue(f.lastResult(analyst).success());

        f.act(analyst, ActionType.ANALYZE, "DB");

        ActionResult second = f.lastResult(analyst);

        assertFalse(second.success());
        assertTrue(second.message().contains("ricarica"));
    }

    @Test
    void analystCannotUseInsiderActions() {
        GameFixture f = new GameFixture(3, 3000).start();
        String analyst = f.analysts().get(0);

        f.act(analyst, ActionType.EXFILTRATE, "DB");
        assertFalse(f.lastResult(analyst).success());

        f.act(analyst, ActionType.JAM, "DB");
        assertFalse(f.lastResult(analyst).success());

        assertEquals(0, f.game.session().exfiltration);
    }

    @Test
    void actionsAreRejectedOutsideInvestigation() {
        GameFixture f = new GameFixture(3, 30);

        assertNull(f.game.startGame(null));

        String analyst = f.analysts().get(0);

        f.act(analyst, ActionType.ANALYZE, "DB");

        assertFalse(f.lastResult(analyst).success());
    }

    @Test
    void repeatingTheSameInsiderMoveRaisesItsCooldown() {
        GameFixture f = new GameFixture(3, 3000).start();
        String insider = f.insider();

        f.tickUntilReady(insider, ActionType.EXFILTRATE);
        f.act(insider, ActionType.EXFILTRATE, "DB");
        int first = f.game.playerNamed(insider).cooldown(ActionType.EXFILTRATE);

        f.tickUntilReady(insider, ActionType.EXFILTRATE);
        f.act(insider, ActionType.EXFILTRATE, "DB");
        int second = f.game.playerNamed(insider).cooldown(ActionType.EXFILTRATE);

        assertEquals(18, first);
        assertEquals(27, second);
    }

    @Test
    void jammedSystemCannotBeAnalyzedOrIsolated() {
        GameFixture f = new GameFixture(3, 3000).start();
        String insider = f.insider();
        String analyst = f.analysts().get(0);

        f.tickUntilReady(insider, ActionType.JAM);
        f.act(insider, ActionType.JAM, "FW");

        assertTrue(f.lastResult(insider).success());

        f.act(analyst, ActionType.ANALYZE, "FW");
        assertFalse(f.lastResult(analyst).success());

        f.act(analyst, ActionType.ISOLATE, "FW");
        assertFalse(f.lastResult(analyst).success());

        GameState state = f.sinks.get(analyst).last(MessageType.GAME_UPDATE, GameState.class);
        String status = state.systems().stream()
                .filter(node -> node.id().equals("FW"))
                .findFirst().orElseThrow().status().name();

        assertEquals("UNKNOWN", status);
    }

    @Test
    void exfiltrationFromJammedSystemLeavesNoTrace() {
        GameFixture f = new GameFixture(3, 3000).start();
        String insider = f.insider();

        f.tickUntilReady(insider, ActionType.JAM);
        f.act(insider, ActionType.JAM, "DB");

        int before = f.game.session().systems.get("DB").log().size();

        f.tickUntilReady(insider, ActionType.EXFILTRATE);
        f.act(insider, ActionType.EXFILTRATE, "DB");

        assertEquals(10, f.game.session().exfiltration);
        assertEquals(before, f.game.session().systems.get("DB").log().size());
    }

    @Test
    void forensicRoleAppearsOnlyWithEnoughPlayers() {
        GameFixture small = new GameFixture(4, 3000).start();
        GameFixture large = new GameFixture(5, 3000).start();

        long smallForensics = small.sinks.keySet().stream().filter(n -> small.roleOf(n) == Role.FORENSIC).count();
        long largeForensics = large.sinks.keySet().stream().filter(n -> large.roleOf(n) == Role.FORENSIC).count();

        assertEquals(0, smallForensics);
        assertEquals(1, largeForensics);
    }

    @Test
    void forensicAnalysisRecoversWipedTraces() {
        GameFixture f = new GameFixture(5, 3000).start();
        String forensic = f.sinks.keySet().stream().filter(n -> f.roleOf(n) == Role.FORENSIC).findFirst().orElseThrow();

        SystemState db = f.game.session().systems.get("DB");
        LogEntry wiped = new LogEntry(f.game.session().newEvidence(db, "HIGH", "Trasferimento anomalo", null),
                LogEntry.Kind.TRACE);
        wiped.setWiped(true);

        for (int i = 0; i < 12; i++) {
            db.append(new LogEntry(f.game.session().newEvidence(db, "HIGH", "Trasferimento anomalo", null),
                    LogEntry.Kind.TRACE));
        }

        db.append(wiped);

        boolean recovered = false;

        for (int attempt = 0; attempt < 12 && !recovered; attempt++) {
            f.tickUntilReady(forensic, ActionType.ANALYZE);
            f.act(forensic, ActionType.ANALYZE, "DB");

            recovered = f.game.playerNamed(forensic).evidence().values().stream()
                    .anyMatch(e -> e.text().startsWith("[RECUPERATA"));
        }

        assertTrue(recovered);
    }

    @Test
    void analystObjectivesProgressAndCountWhenComplete() {
        GameFixture f = new GameFixture(3, 3000).start();
        String analyst = f.analysts().get(0);

        for (String system : List.of("FW", "VPN", "MAIL")) {
            f.tickUntilReady(analyst, ActionType.ANALYZE);
            f.act(analyst, ActionType.ANALYZE, system);
        }

        assertEquals(1, Objectives.completed(f.game.playerNamed(analyst)));

        GameState state = f.sinks.get(analyst).last(MessageType.GAME_UPDATE, GameState.class);

        assertEquals(3, state.objectives().size());
        assertTrue(state.objectives().get(0).startsWith("[x]"));
    }

    @Test
    void emergencyVoteNeedsHalfOfThePlayersAndNotTooEarly() {
        GameFixture f = new GameFixture(3, 3000).start();
        String analyst = f.analysts().get(0);

        f.game.handleCallVote(f.ids.get(analyst));
        assertFalse(f.lastResult(analyst).success());
        assertEquals(Phase.INVESTIGATION, f.game.phase());

        f.tick(45);

        f.game.handleCallVote(f.ids.get(analyst));
        assertEquals(Phase.INVESTIGATION, f.game.phase());
        assertEquals(1, f.game.session().callRequests.size());

        f.game.handleCallVote(f.ids.get(analyst));
        assertFalse(f.lastResult(analyst).success());

        f.game.handleCallVote(f.ids.get(f.analysts().get(1)));
        assertEquals(Phase.VOTING, f.game.phase());
        assertTrue(f.game.session().emergency);
    }

    @Test
    void emergencyVoteOnTheInsiderEndsTheGameForAnalysts() {
        GameFixture f = new GameFixture(3, 3000).start();
        String insider = f.insider();
        List<String> analysts = f.analysts();

        f.tick(45);
        f.game.handleCallVote(f.ids.get(analysts.get(0)));
        f.game.handleCallVote(f.ids.get(analysts.get(1)));

        f.vote(analysts.get(0), insider);
        f.vote(analysts.get(1), insider);
        f.vote(insider, "");
        f.tick(5);

        GameResult result = f.game.lastResult();

        assertEquals(Role.ANALYST, result.winner());
        assertEquals(insider, result.ejected());
    }

    @Test
    void emergencyVoteOnAnInnocentExpelsThemAndResumesTheInvestigation() {
        GameFixture f = new GameFixture(3, 3000).start();
        String insider = f.insider();
        List<String> analysts = f.analysts();
        String victim = analysts.get(1);

        f.tick(45);
        f.game.handleCallVote(f.ids.get(analysts.get(0)));
        f.game.handleCallVote(f.ids.get(analysts.get(1)));

        int timeBefore = f.game.session().savedSecondsLeft;

        f.vote(analysts.get(0), victim);
        f.vote(insider, victim);
        f.vote(victim, analysts.get(0));
        f.tick(5);

        assertEquals(Phase.INVESTIGATION, f.game.phase());
        assertFalse(f.game.session().emergency);
        assertTrue(f.game.playerNamed(victim).isExpelled());
        assertEquals(GameSession.EMERGENCY_PENALTY, f.game.session().exfiltration);
        assertTrue(f.game.session().secondsLeft >= timeBefore - 5);

        f.act(victim, ActionType.ANALYZE, "DB");
        assertFalse(f.lastResult(victim).success());

        f.game.handleCallVote(f.ids.get(analysts.get(0)));
        assertFalse(f.lastResult(analysts.get(0)).success());
    }

    @Test
    void inconclusiveEmergencyVoteResumesWithoutPenalty() {
        GameFixture f = new GameFixture(3, 3000).start();
        List<String> analysts = f.analysts();

        f.tick(45);
        f.game.handleCallVote(f.ids.get(analysts.get(0)));
        f.game.handleCallVote(f.ids.get(analysts.get(1)));

        f.vote(analysts.get(0), "");
        f.vote(analysts.get(1), "");
        f.vote(f.insider(), "");
        f.tick(5);

        assertEquals(Phase.INVESTIGATION, f.game.phase());
        assertEquals(0, f.game.session().exfiltration);
        assertTrue(f.sinks.keySet().stream().noneMatch(n -> f.game.playerNamed(n).isExpelled()));
    }

    @Test
    void emergencyVotesAreLimitedPerSession() {
        GameFixture f = new GameFixture(3, 3000).start();
        List<String> analysts = f.analysts();

        for (int round = 0; round < GameSession.EMERGENCY_MAX; round++) {
            f.tick(GameSession.EMERGENCY_COOLDOWN + 50);

            f.game.handleCallVote(f.ids.get(analysts.get(0)));
            f.game.handleCallVote(f.ids.get(analysts.get(1)));

            assertEquals(Phase.VOTING, f.game.phase());

            f.vote(analysts.get(0), "");
            f.vote(analysts.get(1), "");
            f.vote(f.insider(), "");
            f.tick(5);
        }

        f.tick(GameSession.EMERGENCY_COOLDOWN + 5);
        f.game.handleCallVote(f.ids.get(analysts.get(0)));

        assertFalse(f.lastResult(analysts.get(0)).success());
        assertEquals(Phase.INVESTIGATION, f.game.phase());
    }

    @Test
    void droppedPlayerCanRejoinWithinTheGracePeriod() {
        GameFixture f = new GameFixture(3, 3000).start();
        String name = f.analysts().get(0);
        Role role = f.roleOf(name);

        String oldId = f.ids.get(name);
        f.connections.unregister(oldId);
        f.game.playerLeft(oldId);

        assertFalse(f.game.playerNamed(name).isConnected());

        f.tick(10);

        GameFixture.RecordingSink fresh = new GameFixture.RecordingSink();
        String newId = f.connections.register(fresh);
        f.connections.setPlayerName(newId, name);

        assertTrue(f.game.canRejoin(name));
        assertTrue(f.game.rejoin(newId, name));

        assertTrue(f.game.playerNamed(name).isConnected());
        assertEquals(newId, f.game.playerNamed(name).clientId());
        assertEquals(1, fresh.count(MessageType.ROLE_ASSIGN));
        assertEquals(1, fresh.count(MessageType.GAME_START));
        assertEquals(role, fresh.last(MessageType.ROLE_ASSIGN, it.soc.common.model.RoleInfo.class).role());

        f.game.handleAction(newId, new it.soc.common.model.PlayerAction(ActionType.ANALYZE, "DB", null));

        assertTrue(fresh.last(MessageType.ACTION_RESULT, ActionResult.class).success());
    }

    @Test
    void droppedPlayerCannotRejoinAfterTheGracePeriod() {
        GameFixture f = new GameFixture(4, 3000).start();
        String name = f.analysts().get(0);

        String oldId = f.ids.get(name);
        f.connections.unregister(oldId);
        f.game.playerLeft(oldId);

        f.tick(GameSession.RECONNECT_GRACE + 2);

        assertFalse(f.game.canRejoin(name));
        assertTrue(f.game.playerNamed(name).isExpelled());
    }

    @Test
    void insiderWhoDoesNotComeBackLosesTheGame() {
        GameFixture f = new GameFixture(3, 3000).start();
        String insider = f.insider();

        String oldId = f.ids.get(insider);
        f.connections.unregister(oldId);
        f.game.playerLeft(oldId);

        f.tick(GameSession.RECONNECT_GRACE + 2);

        assertNotNull(f.game.lastResult());
        assertEquals(Role.ANALYST, f.game.lastResult().winner());
        assertEquals(Phase.LOBBY, f.game.phase());
    }

    @Test
    void gameEndsWhenEveryoneLeaves() {
        GameFixture f = new GameFixture(3, 3000).start();

        for (String id : List.copyOf(f.ids.values())) {
            f.connections.unregister(id);
            f.game.playerLeft(id);
        }

        assertEquals(Phase.LOBBY, f.game.phase());
        assertNull(f.game.lastResult().winner());
    }

    @Test
    void whisperReachesOnlySenderAndTarget() {
        GameFixture f = new GameFixture(3, 3000).start();

        f.game.handleChat(f.ids.get("Alice"), "/w Bruno ciao in privato");

        assertEquals(1, f.sinks.get("Alice").count(MessageType.CHAT));
        assertEquals(1, f.sinks.get("Bruno").count(MessageType.CHAT));
        assertEquals(0, f.sinks.get("Carla").count(MessageType.CHAT));

        ChatMessage message = f.sinks.get("Bruno").last(MessageType.CHAT, ChatMessage.class);

        assertEquals("Alice", message.from());
        assertEquals("Bruno", message.to());
        assertEquals("ciao in privato", message.text());
    }

    @Test
    void whisperToUnknownPlayerIsRefused() {
        GameFixture f = new GameFixture(3, 3000).start();

        f.game.handleChat(f.ids.get("Alice"), "/w Nessuno ciao");

        assertEquals(0, f.sinks.get("Bruno").count(MessageType.CHAT));
        assertFalse(f.lastResult("Alice").success());
    }

    @Test
    void publicChatReachesEveryone() {
        GameFixture f = new GameFixture(3, 3000).start();

        f.game.handleChat(f.ids.get("Alice"), "Controllo il database");

        for (String name : GameFixture.NAMES) {
            if (f.sinks.containsKey(name)) {
                assertEquals(1, f.sinks.get(name).count(MessageType.CHAT));
            }
        }
    }

    @Test
    void sharingEvidenceCountsTowardsObjectivesOnlyOnce() {
        GameFixture f = new GameFixture(3, 3000).start();
        String analyst = f.analysts().get(0);

        f.act(analyst, ActionType.ANALYZE, "DB");

        String evidenceId = f.game.playerNamed(analyst).evidence().keySet().iterator().next();

        f.game.handleShare(f.ids.get(analyst), evidenceId);
        f.game.handleShare(f.ids.get(analyst), evidenceId);

        assertEquals(1, f.game.playerNamed(analyst).shared());
    }

    @Test
    void difficultyChangesTheRules() {
        GameConfig config = new GameConfig();

        config.setDifficulty(GameConfig.Difficulty.EASY);
        assertEquals(8, config.exfiltrationStep());
        assertEquals(2, config.blocksToWin());
        assertEquals(270, config.investigationSeconds());

        config.setDifficulty(GameConfig.Difficulty.HARD);
        assertEquals(12, config.exfiltrationStep());
        assertEquals(4, config.blocksToWin());
        assertEquals(210, config.investigationSeconds());
        assertTrue(config.insiderCooldown(20) < 20);
    }

    @Test
    void scoresAreAssignedToEveryPlayerWhenTheGameEnds() {
        GameFixture f = new GameFixture(3, 30).start();
        String insider = f.insider();
        List<String> analysts = f.analysts();

        f.tickUntil(Phase.VOTING);

        f.vote(analysts.get(0), insider);
        f.vote(analysts.get(1), insider);
        f.vote(insider, "");
        f.tick(5);

        GameResult result = f.game.lastResult();

        assertEquals(3, result.scores().size());
        assertTrue(result.scores().get(analysts.get(0)) >= 125);
        assertEquals(3, result.records().size());
        assertTrue(result.records().get(insider).contains("Partite 1"));
    }
}
