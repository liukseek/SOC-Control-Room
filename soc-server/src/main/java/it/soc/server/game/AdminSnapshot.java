package it.soc.server.game;

import it.soc.common.model.Phase;
import it.soc.common.model.Role;
import java.util.List;

public record AdminSnapshot(
        Phase phase,
        int secondsLeft,
        int phaseDuration,
        int exfiltration,
        List<String> lobby,
        String host,
        List<Player> players
) {

    public record Player(
            String name,
            String workstation,
            Role role,
            boolean connected,
            boolean voted,
            int actions,
            int evidence
    ) {}
}
