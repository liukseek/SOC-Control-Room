package it.soc.common.model;

import java.util.List;

public record RoleInfo(
        Role role,
        String workstation,
        String title,
        String briefing,
        List<String> objectives
) {}
