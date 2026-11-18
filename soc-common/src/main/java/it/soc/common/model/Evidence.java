package it.soc.common.model;

public record Evidence(
        String id,
        String system,
        String time,
        String severity,
        String text,
        String origin
) {}
