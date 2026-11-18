package it.soc.common.model;

import java.util.List;

public record LobbyState(
        List<String> players,
        String host,
        int minPlayers,
        boolean gameInProgress
) {}
