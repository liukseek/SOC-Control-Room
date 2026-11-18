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
        Map<ActionType, Integer> cooldowns
) {}
