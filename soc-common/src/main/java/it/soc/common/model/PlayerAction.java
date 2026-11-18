package it.soc.common.model;

public record PlayerAction(
        ActionType action,
        String target,
        String subject
) {}
