package it.soc.common.model;

public record ChatMessage(
        String from,
        String text,
        Evidence evidence,
        String time,
        String to
) {}
