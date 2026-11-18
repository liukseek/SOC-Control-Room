package it.soc.common.model;

public record SystemNode(
        String id,
        String name,
        String description,
        SystemStatus status,
        boolean alert,
        int logCount
) {}
