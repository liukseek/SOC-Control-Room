package it.soc.common.model;

import java.util.List;
import java.util.Map;

public record GameResult(
        Role winner,
        String reason,
        String insider,
        String ejected,
        Map<String, Integer> votes,
        int exfiltration,
        int evidenceCollected,
        int evidenceShared,
        List<String> insiderLog
) {}
