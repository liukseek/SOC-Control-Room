package it.soc.common;

public record Message(
    MessageType type,
    String sender,
    String content
) {}
