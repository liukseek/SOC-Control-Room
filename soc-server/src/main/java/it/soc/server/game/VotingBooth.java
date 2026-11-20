package it.soc.server.game;

import it.soc.common.model.GameEvent;
import it.soc.common.model.Phase;
import it.soc.common.model.Role;
import it.soc.server.ServerLog;
import java.util.LinkedHashMap;
import java.util.Map;

class VotingBooth {

    record Tally(Map<String, Integer> votes, int skipped, String ejected) {}

    private final GameSession session;
    private final GameMessenger messenger;
    private final GameOutcome outcome;

    VotingBooth(GameSession session, GameMessenger messenger, GameOutcome outcome) {
        this.session = session;
        this.messenger = messenger;
        this.outcome = outcome;
    }

    void handleVote(String clientId, String target) {
        PlayerState player = session.players.get(clientId);

        if (player == null || session.phase != Phase.VOTING || !player.isActive()) {
            return;
        }

        String vote = target == null ? "" : target.strip();

        if (!vote.isEmpty()) {
            boolean valid = false;

            for (PlayerState other : session.players.values()) {
                if (other.isActive() && other.name().equals(vote) && other != player) {
                    valid = true;
                }
            }

            if (!valid) {
                messenger.reply(player, null, false, "Voto non valido.");
                return;
            }
        }

        player.setVote(vote);
        ServerLog.game(player.name() + " ha votato.");

        messenger.broadcastState();
    }

    boolean allVoted() {
        for (PlayerState player : session.players.values()) {
            if (player.isActive() && player.vote() == null) {
                return false;
            }
        }

        return true;
    }

    Tally tally() {
        Map<String, Integer> votes = new LinkedHashMap<>();
        int skipped = 0;

        for (PlayerState player : session.players.values()) {
            if (player.isActive()) {
                votes.put(player.name(), 0);
            }
        }

        for (PlayerState player : session.players.values()) {
            if (!player.isActive() || player.vote() == null) {
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

        return new Tally(votes, skipped, ejected);
    }

    void finishFinal() {
        Tally tally = tally();
        String ejected = tally.ejected();
        PlayerState insider = session.insider();
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

        outcome.finish(winner, reason, ejected, tally.votes());
    }

    void callVote(String clientId) {
        PlayerState player = session.players.get(clientId);

        if (player == null) {
            return;
        }

        String refusal = refusal(player);

        if (refusal != null) {
            messenger.reply(player, null, false, refusal);
            return;
        }

        session.callRequests.add(clientId);

        if (session.callRequests.size() >= session.requiredCalls()) {
            startEmergency();
            return;
        }

        messenger.sendEvent("VOTE_CALL", "Voto anticipato richiesto",
                player.name() + " chiede di interrompere l'indagine e votare ("
                        + session.callRequests.size() + "/" + session.requiredCalls() + ").",
                null, "WARN");

        messenger.broadcastState();
    }

    private String refusal(PlayerState player) {
        if (player.isExpelled()) {
            return "Sei stato espulso dalla rete.";
        }

        if (session.phase != Phase.INVESTIGATION) {
            return "Il voto anticipato è disponibile solo durante l'indagine.";
        }

        if (session.elapsed < GameSession.EMERGENCY_MIN_ELAPSED) {
            return "Troppo presto: servono almeno " + GameSession.EMERGENCY_MIN_ELAPSED
                    + " secondi di indagine.";
        }

        if (session.emergencyCalls >= GameSession.EMERGENCY_MAX) {
            return "I voti anticipati di questa sessione sono esauriti.";
        }

        if (session.emergencyCooldown > 0) {
            return "Attendi " + session.emergencyCooldown + " s prima di una nuova richiesta.";
        }

        if (session.callRequests.contains(player.clientId())) {
            return "Hai già chiesto il voto anticipato.";
        }

        return null;
    }

    private void startEmergency() {
        session.emergency = true;
        session.savedSecondsLeft = session.secondsLeft;
        session.savedDuration = session.phaseDuration;
        session.phase = Phase.VOTING;
        session.phaseDuration = GameSession.EMERGENCY_VOTE_SECONDS;
        session.secondsLeft = GameSession.EMERGENCY_VOTE_SECONDS;
        session.emergencyCalls++;
        session.callRequests.clear();

        for (PlayerState player : session.players.values()) {
            player.setVote(null);
        }

        messenger.sendEvent(new GameEvent(
                "PHASE",
                "Votazione di emergenza",
                "L'indagine è sospesa e l'esfiltrazione è ferma. Avete "
                        + GameSession.EMERGENCY_VOTE_SECONDS + " secondi per votare.",
                null,
                "WARN"
        ), p -> true);

        ServerLog.game("Votazione di emergenza avviata (" + session.emergencyCalls + "/"
                + GameSession.EMERGENCY_MAX + ").");
    }

    void resolveEmergency() {
        Tally tally = tally();
        PlayerState ejected = tally.ejected() == null ? null : session.byName(tally.ejected());

        if (ejected == null) {
            messenger.sendEvent("INFO", "Voto inconcluso",
                    "Nessun operatore ha ottenuto la maggioranza: l'indagine riprende.", null, "INFO");
            resume();
            return;
        }

        if (ejected.isInsider()) {
            outcome.finish(Role.ANALYST,
                    "L'insider è stato identificato ed espulso nella votazione di emergenza.",
                    ejected.name(),
                    tally.votes());
            return;
        }

        ejected.setExpelled(true);
        session.exfiltration = Math.min(100, session.exfiltration + GameSession.EMERGENCY_PENALTY);
        session.insiderLog.add(session.clock() + "  Un innocente è stato espulso: " + ejected.name()
                + " (esfiltrazione " + session.exfiltration + "%)");

        messenger.sendEvent("INFO", "Innocente espulso",
                ejected.name() + " non era l'insider. L'esfiltrazione sale al "
                        + session.exfiltration + "%.",
                null, "CRITICAL");

        if (session.exfiltration >= 100) {
            outcome.finish(Role.INSIDER,
                    ejected.name() + " era innocente e l'esfiltrazione ha raggiunto il 100%.",
                    ejected.name(),
                    tally.votes());
            return;
        }

        if (session.activeAnalysts() == 0) {
            outcome.finish(Role.INSIDER,
                    "Tutti gli analisti sono stati espulsi o disconnessi.",
                    ejected.name(),
                    tally.votes());
            return;
        }

        resume();
    }

    private void resume() {
        session.emergency = false;
        session.phase = Phase.INVESTIGATION;
        session.secondsLeft = session.savedSecondsLeft;
        session.phaseDuration = session.savedDuration;
        session.emergencyCooldown = GameSession.EMERGENCY_COOLDOWN;

        for (PlayerState player : session.players.values()) {
            player.setVote(null);
        }

        messenger.sendEvent(new GameEvent(
                "PHASE",
                "Indagine ripresa",
                "Il tempo riparte da dove si era fermato.",
                null,
                "INFO"
        ), p -> true);
    }
}
