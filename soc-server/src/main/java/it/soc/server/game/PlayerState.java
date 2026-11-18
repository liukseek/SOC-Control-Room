package it.soc.server.game;

import it.soc.common.model.ActionType;
import it.soc.common.model.Evidence;
import it.soc.common.model.Role;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class PlayerState {

    private final String clientId;
    private final String name;
    private final String workstation;
    private final Role role;
    private final Map<ActionType, Integer> cooldowns = new EnumMap<>(ActionType.class);
    private final Map<String, Evidence> evidence = new LinkedHashMap<>();

    private boolean connected = true;
    private String vote;
    private long lastChat;
    private int actions;

    public PlayerState(String clientId, String name, String workstation, Role role) {
        this.clientId = clientId;
        this.name = name;
        this.workstation = workstation;
        this.role = role;

        cooldowns.put(ActionType.ANALYZE, 0);
        cooldowns.put(ActionType.ISOLATE, 0);

        if (role == Role.INSIDER) {
            cooldowns.put(ActionType.EXFILTRATE, 5);
            cooldowns.put(ActionType.WIPE, 10);
            cooldowns.put(ActionType.FRAME, 20);
        }
    }

    public String clientId() {
        return clientId;
    }

    public String name() {
        return name;
    }

    public String workstation() {
        return workstation;
    }

    public String account() {
        return "op." + name.toLowerCase().replace(' ', '.');
    }

    public Role role() {
        return role;
    }

    public boolean isInsider() {
        return role == Role.INSIDER;
    }

    public boolean canUse(ActionType action) {
        return cooldowns.containsKey(action);
    }

    public int cooldown(ActionType action) {
        return cooldowns.getOrDefault(action, 0);
    }

    public void startCooldown(ActionType action, int seconds) {
        cooldowns.put(action, seconds);
    }

    public void tickCooldowns() {
        cooldowns.replaceAll((action, value) -> Math.max(0, value - 1));
    }

    public Map<ActionType, Integer> cooldowns() {
        return new EnumMap<>(cooldowns);
    }

    public Map<String, Evidence> evidence() {
        return evidence;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public String vote() {
        return vote;
    }

    public void setVote(String vote) {
        this.vote = vote;
    }

    public long lastChat() {
        return lastChat;
    }

    public void setLastChat(long lastChat) {
        this.lastChat = lastChat;
    }

    public int actions() {
        return actions;
    }

    public void countAction() {
        actions++;
    }
}
