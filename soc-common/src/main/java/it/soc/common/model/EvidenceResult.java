package it.soc.common.model;

import java.util.List;

public record EvidenceResult(
        String system,
        List<Evidence> entries,
        String note
) {}
