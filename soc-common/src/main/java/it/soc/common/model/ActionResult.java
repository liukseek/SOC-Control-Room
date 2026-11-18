package it.soc.common.model;

public record ActionResult(
        ActionType action,
        boolean success,
        String message,
        int cooldown
) {}
