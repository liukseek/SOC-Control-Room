package it.soc.common.model;

public record PlayerInfo(
        String name,
        String workstation,
        boolean voted,
        boolean connected
) {}
