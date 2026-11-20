package it.soc.server.game;

import it.soc.common.model.ActionType;
import it.soc.common.model.Evidence;
import it.soc.common.model.Role;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class PlayerState {

    private final String name;
    private final String workstation;
    private final Role role;
    private final Map<ActionType, Integer> cooldowns = new EnumMap<>(ActionType.class);
    private final Map<String, Evidence> evidence = new LinkedHashMap<>();
    private final Set<String> analyzedSystems = new HashSet<>();

    private String clientId;
    private boolean connected = true;
    private boolean expelled;
    private int disconnectedFor;
    private String vote;
    private long lastChat;
    private int actions;
    private int shared;
    private int blocks;
    private int wipes;
    private int frames;
    private int jams;
    private ActionType lastAction;

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
            cooldowns.put(ActionType.JAM, 15);
        }
    }

    public String clientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
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

    public boolean isForensic() {
        return role == Role.FORENSIC;
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

    public Set<String> analyzedSystems() {
        return analyzedSystems;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;

        if (connected) {
            disconnectedFor = 0;
        }
    }

    public boolean isExpelled() {
        return expelled;
    }

    public void setExpelled(boolean expelled) {
        this.expelled = expelled;
    }

    public boolean isActive() {
        return connected && !expelled;
    }

    public int disconnectedFor() {
        return disconnectedFor;
    }

    public void tickDisconnected() {
        disconnectedFor++;
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

    public int shared() {
        return shared;
    }

    public void countShared() {
        shared++;
    }

    public int blocks() {
        return blocks;
    }

    public void countBlock() {
        blocks++;
    }

    public int wipes() {
        return wipes;
    }

    public void countWipe() {
        wipes++;
    }

    public int frames() {
        return frames;
    }

    public void countFrame() {
        frames++;
    }

    public int jams() {
        return jams;
    }

    public void countJam() {
        jams++;
    }

    public boolean registerAction(ActionType action) {
        boolean repeated = action == lastAction;
        lastAction = action;
        return repeated;
    }
}
