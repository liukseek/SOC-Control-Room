package it.soc.common.model;

import java.util.List;
import java.util.Map;

public record GameState(
        Phase phase,
        int secondsLeft,
        int phaseDuration,
        int exfiltration,
        List<SystemNode> systems,
        List<PlayerInfo> players,
        Map<ActionType, Integer> cooldowns,
        int blocked,
        int blockLimit,
        List<String> objectives,
        boolean emergency,
        int callVotes,
        int callVotesNeeded,
        boolean canCallVote
) {}
