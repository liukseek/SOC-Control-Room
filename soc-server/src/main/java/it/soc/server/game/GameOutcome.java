package it.soc.server.game;

import it.soc.common.model.Role;
import java.util.Map;

interface GameOutcome {

    void finish(Role winner, String reason, String ejected, Map<String, Integer> votes);
}
