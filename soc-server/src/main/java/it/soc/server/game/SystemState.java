package it.soc.server.game;

import java.util.ArrayList;
import java.util.List;

public class SystemState {

    private static final int MAX_LOG = 45;

    private final String id;
    private final String name;
    private final String description;
    private final List<LogEntry> log = new ArrayList<>();

    private int isolatedFor;
    private String isolatedBy;
    private int maintenanceFor;
    private int honeypotFor;
    private int alertFor;

    public SystemState(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public List<LogEntry> log() {
        return log;
    }

    public void append(LogEntry entry) {
        log.add(entry);

        if (log.size() > MAX_LOG) {
            for (int i = 0; i < log.size(); i++) {
                if (log.get(i).kind() == LogEntry.Kind.NOISE) {
                    log.remove(i);
                    break;
                }
            }
        }
    }

    public List<LogEntry> visibleLog() {
        List<LogEntry> visible = new ArrayList<>();

        for (LogEntry entry : log) {
            if (!entry.isWiped()) {
                visible.add(entry);
            }
        }

        return visible;
    }

    public void tick() {
        if (isolatedFor > 0 && --isolatedFor == 0) {
            isolatedBy = null;
        }

        if (maintenanceFor > 0) {
            maintenanceFor--;
        }

        if (honeypotFor > 0) {
            honeypotFor--;
        }

        if (alertFor > 0) {
            alertFor--;
        }
    }

    public boolean isIsolated() {
        return isolatedFor > 0;
    }

    public String isolatedBy() {
        return isolatedBy;
    }

    public void isolate(String clientId, int seconds) {
        isolatedFor = seconds;
        isolatedBy = clientId;
    }

    public boolean inMaintenance() {
        return maintenanceFor > 0;
    }

    public void setMaintenance(int seconds) {
        maintenanceFor = seconds;
    }

    public boolean isHoneypot() {
        return honeypotFor > 0;
    }

    public void setHoneypot(int seconds) {
        honeypotFor = seconds;
    }

    public boolean hasAlert() {
        return alertFor > 0;
    }

    public void raiseAlert(int seconds) {
        alertFor = Math.max(alertFor, seconds);
    }
}
