package it.soc.common.model;

public record GameEvent(
        String kind,
        String title,
        String description,
        String system,
        String severity
) {}
