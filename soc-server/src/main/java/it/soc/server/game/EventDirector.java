package it.soc.server.game;

import it.soc.common.model.ActionType;
import it.soc.common.model.GameEvent;
import it.soc.server.ServerLog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class EventDirector {

    static final int MAINTENANCE_SECONDS = 20;
    static final int HONEYPOT_SECONDS = 30;

    private final GameSession session;
    private final GameMessenger messenger;

    EventDirector(GameSession session, GameMessenger messenger) {
        this.session = session;
        this.messenger = messenger;
    }

    int nextDelay() {
        if (session.exfiltration >= 60) {
            return 20 + session.random.nextInt(10);
        }

        return 35 + session.random.nextInt(15);
    }

    void trigger() {
        List<String> pool = new ArrayList<>(List.of(
                "SIEM_REPORT", "SIEM_REPORT", "BACKUP", "TRAFFIC",
                "MAINTENANCE", "HONEYPOT", "HONEYPOT"
        ));

        if (!session.badgeAuditDone) {
            pool.add("BADGE");
            pool.add("BADGE");
        }

        if (session.exfiltration >= 30) {
            pool.add("SIEM_REPORT");
            pool.add("SIEM_REPORT");
        }

        if (session.exfiltration >= 60) {
            pool.add("AUDIT");
            pool.add("AUDIT");
            pool.add("AUDIT");
        }

        if (hasWipedEntries()) {
            pool.add("BACKUP");
            pool.add("BACKUP");
        }

        pool.removeIf(kind -> kind.equals(session.lastEventKind));

        String kind = pool.get(session.random.nextInt(pool.size()));
        session.lastEventKind = kind;

        switch (kind) {
            case "SIEM_REPORT" -> siemReport();
            case "BACKUP" -> backupRestore();
            case "TRAFFIC" -> trafficSpike();
            case "BADGE" -> badgeAudit();
            case "MAINTENANCE" -> maintenance();
            case "AUDIT" -> emergencyAudit();
            default -> honeypot();
        }

        ServerLog.game("Evento: " + kind);
    }

    private boolean hasWipedEntries() {
        for (SystemState system : session.systems.values()) {
            for (LogEntry entry : system.log()) {
                if (entry.isWiped()) {
                    return true;
                }
            }
        }

        return false;
    }

    private void siemReport() {
        SystemState system = session.lastExfilSystem == null ? null : session.systems.get(session.lastExfilSystem);

        if (system == null) {
            messenger.sendEvent("SIEM_REPORT", "Report SIEM",
                    "Nessun trasferimento anomalo confermato finora. Restate all'erta.", null, "INFO");
            return;
        }

        system.raiseAlert(8);

        messenger.sendEvent("SIEM_REPORT", "Report SIEM",
                "L'ultimo trasferimento sospetto è partito da " + system.name() + ".",
                system.id(), "WARN");
    }

    private void emergencyAudit() {
        SystemState system = session.lastExfilSystem == null ? null : session.systems.get(session.lastExfilSystem);

        if (system == null) {
            siemReport();
            return;
        }

        system.raiseAlert(12);

        for (PlayerState player : session.players.values()) {
            if (!player.isInsider() && player.canUse(ActionType.ANALYZE)) {
                player.startCooldown(ActionType.ANALYZE, 0);
            }
        }

        messenger.sendEvent(new GameEvent(
                "AUDIT",
                "Audit di emergenza",
                "La direzione impone un audit: l'ultimo trasferimento è partito da "
                        + system.name() + " e i tempi di analisi degli analisti sono azzerati.",
                system.id(),
                "CRITICAL"
        ), p -> true);
    }

    private void backupRestore() {
        List<SystemState> candidates = new ArrayList<>();

        for (SystemState system : session.systems.values()) {
            for (LogEntry entry : system.log()) {
                if (entry.isWiped()) {
                    candidates.add(system);
                    break;
                }
            }
        }

        if (candidates.isEmpty()) {
            SystemState system = session.randomSystem();

            messenger.sendEvent("BACKUP", "Backup verificato",
                    "Il backup di " + system.name() + " è integro: nessuna voce mancante.",
                    system.id(), "INFO");
            return;
        }

        SystemState system = candidates.get(session.random.nextInt(candidates.size()));
        int restored = 0;

        for (LogEntry entry : system.log()) {
            if (entry.isWiped()) {
                entry.setWiped(false);
                restored++;
            }
        }

        messenger.sendEvent("BACKUP", "Log ripristinati",
                "Il backup notturno ha recuperato " + restored
                        + (restored == 1 ? " voce cancellata" : " voci cancellate")
                        + " su " + system.name() + ".",
                system.id(), "SUCCESS");
    }

    private void trafficSpike() {
        for (SystemState system : session.systems.values()) {
            for (int i = 0; i < 3; i++) {
                session.addNoise(system);
            }
        }

        messenger.sendEvent("TRAFFIC", "Picco di traffico",
                "Un aumento improvviso di attività ha riempito i log di rumore.", null, "INFO");
    }

    private void badgeAudit() {
        PlayerState insider = session.insider();
        List<PlayerState> others = new ArrayList<>();

        for (PlayerState player : session.players.values()) {
            if (player.isConnected() && player != insider) {
                others.add(player);
            }
        }

        if (insider == null || others.isEmpty()) {
            siemReport();
            return;
        }

        session.badgeAuditDone = true;

        List<String> names = new ArrayList<>(List.of(
                insider.name(),
                others.get(session.random.nextInt(others.size())).name()
        ));

        Collections.shuffle(names, session.random);

        messenger.sendEvent("BADGE", "Registro badge",
                names.get(0) + " e " + names.get(1)
                        + " sono entrati nella sala server alle " + session.clock().substring(0, 5) + ".",
                null, "WARN");
    }

    private void maintenance() {
        List<SystemState> candidates = new ArrayList<>();

        for (SystemState system : session.systems.values()) {
            if (!system.inMaintenance()) {
                candidates.add(system);
            }
        }

        SystemState system = candidates.get(session.random.nextInt(candidates.size()));
        system.setMaintenance(MAINTENANCE_SECONDS);

        messenger.sendEvent("MAINTENANCE", "Manutenzione programmata",
                system.name() + " non sarà disponibile per " + MAINTENANCE_SECONDS + " secondi.",
                system.id(), "WARN");
    }

    private void honeypot() {
        List<SystemState> candidates = new ArrayList<>();

        for (SystemState system : session.systems.values()) {
            if (!system.inMaintenance() && !system.isIsolated() && !system.isHoneypot()) {
                candidates.add(system);
            }
        }

        if (candidates.isEmpty()) {
            trafficSpike();
            return;
        }

        SystemState system = candidates.get(session.random.nextInt(candidates.size()));
        system.setHoneypot(HONEYPOT_SECONDS);

        messenger.sendEvent(new GameEvent(
                "HONEYPOT",
                "Honeypot attivo",
                "Dati esca piazzati su " + system.name() + " per " + HONEYPOT_SECONDS
                        + " secondi. Non dirlo in chat: l'insider potrebbe leggere.",
                system.id(),
                "SUCCESS"
        ), p -> !p.isInsider());
    }
}
