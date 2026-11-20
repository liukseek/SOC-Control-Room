package it.soc.server.game;

import it.soc.common.MessageType;
import it.soc.common.Protocol;
import it.soc.common.model.ActionType;
import it.soc.common.model.Evidence;
import it.soc.common.model.EvidenceResult;
import it.soc.common.model.GameEvent;
import it.soc.common.model.Phase;
import it.soc.common.model.PlayerAction;
import it.soc.common.model.Role;
import it.soc.server.GameConfig;
import it.soc.server.ServerLog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

class PlayerActions {

    static final int ISOLATION_SECONDS = 20;
    static final int JAM_SECONDS = 15;

    private static final double REPEAT_COOLDOWN_FACTOR = 1.5;
    private static final double REPEAT_EXPOSURE = 0.25;

    private static final Map<ActionType, Integer> COOLDOWNS = new EnumMap<>(Map.of(
            ActionType.ANALYZE, 6,
            ActionType.ISOLATE, 35,
            ActionType.EXFILTRATE, 18,
            ActionType.WIPE, 25,
            ActionType.FRAME, 35,
            ActionType.JAM, 30
    ));

    private final GameSession session;
    private final GameMessenger messenger;
    private final GameConfig config;
    private final GameOutcome outcome;

    private boolean repeated;

    PlayerActions(GameSession session, GameMessenger messenger, GameConfig config, GameOutcome outcome) {
        this.session = session;
        this.messenger = messenger;
        this.config = config;
        this.outcome = outcome;
    }

    void handle(String clientId, PlayerAction action) {
        PlayerState player = session.players.get(clientId);

        if (player == null || action == null || action.action() == null) {
            return;
        }

        ActionType type = action.action();

        if (player.isExpelled()) {
            messenger.reply(player, type, false, "Sei stato espulso dalla rete: non puoi più agire.");
            return;
        }

        if (session.phase != Phase.INVESTIGATION) {
            messenger.reply(player, type, false, "Le azioni sono disponibili solo durante l'indagine.");
            return;
        }

        if (!player.canUse(type)) {
            messenger.reply(player, type, false, "Azione non autorizzata per il tuo ruolo.");
            ServerLog.warn("Azione non autorizzata da " + player.name() + ": " + type);
            return;
        }

        if (player.cooldown(type) > 0) {
            messenger.reply(player, type, false, "Azione in ricarica.");
            return;
        }

        SystemState system = action.target() == null ? null : session.systems.get(action.target());

        if (system == null) {
            messenger.reply(player, type, false, "Seleziona un sistema valido.");
            return;
        }

        player.countAction();

        repeated = player.isInsider() && isInsiderMove(type) && player.registerAction(type);

        if (player.isInsider() && !isInsiderMove(type)) {
            player.registerAction(type);
        }

        switch (type) {
            case ANALYZE -> analyze(player, system);
            case ISOLATE -> isolate(player, system);
            case EXFILTRATE -> exfiltrate(player, system);
            case WIPE -> wipe(player, system);
            case FRAME -> frame(player, system, action.subject());
            case JAM -> jam(player, system);
        }

        if (session.phase != Phase.LOBBY) {
            messenger.sendState(player);
        }
    }

    private static boolean isInsiderMove(ActionType type) {
        return type == ActionType.EXFILTRATE || type == ActionType.WIPE
                || type == ActionType.FRAME || type == ActionType.JAM;
    }

    private void cooldown(PlayerState player, ActionType action) {
        int seconds = COOLDOWNS.get(action);

        if (player.isInsider() && isInsiderMove(action)) {
            seconds = config.insiderCooldown(seconds);

            if (repeated) {
                seconds = (int) Math.round(seconds * REPEAT_COOLDOWN_FACTOR);
            }
        }

        player.startCooldown(action, seconds);
    }

    private double exposure(double base) {
        return Math.min(0.95, base + (repeated ? REPEAT_EXPOSURE : 0));
    }

    private boolean blocked(PlayerState player, ActionType action, SystemState system) {
        if (system.inMaintenance()) {
            messenger.reply(player, action, false, system.name() + " è in manutenzione.");
            return true;
        }

        return false;
    }

    private void analyze(PlayerState player, SystemState system) {
        if (blocked(player, ActionType.ANALYZE, system)) {
            return;
        }

        if (system.isJammed()) {
            messenger.reply(player, ActionType.ANALYZE, false,
                    "Segnale disturbato su " + system.name() + ": i log non sono leggibili.");
            return;
        }

        List<LogEntry> visible = system.visibleLog();
        List<LogEntry> suspicious = new ArrayList<>();
        List<LogEntry> others = new ArrayList<>();

        for (LogEntry entry : visible) {
            if (entry.isSuspicious()) {
                suspicious.add(entry);
            } else {
                others.add(entry);
            }
        }

        Collections.shuffle(suspicious, session.random);
        Collections.shuffle(others, session.random);

        int limit = player.isForensic() ? 6 : 4;
        List<LogEntry> picked = new ArrayList<>();

        for (LogEntry entry : suspicious) {
            if (picked.size() < limit && session.random.nextDouble() < 0.7) {
                picked.add(entry);
            }
        }

        for (LogEntry entry : others) {
            if (picked.size() >= limit) {
                break;
            }

            picked.add(entry);
        }

        picked.sort((a, b) -> Integer.compare(visible.indexOf(a), visible.indexOf(b)));

        List<Evidence> entries = new ArrayList<>();

        for (LogEntry entry : picked) {
            entries.add(entry.evidence());
            player.evidence().put(entry.evidence().id(), entry.evidence());
        }

        int recovered = 0;

        if (player.isForensic()) {
            for (LogEntry entry : system.log()) {
                if (entry.isWiped() && entry.kind() == LogEntry.Kind.TRACE
                        && session.random.nextDouble() < 0.6) {
                    Evidence original = entry.evidence();
                    Evidence copy = new Evidence(
                            original.id(),
                            original.system(),
                            original.time(),
                            original.severity(),
                            "[RECUPERATA DAL DISCO] " + original.text(),
                            original.origin()
                    );

                    entries.add(copy);
                    player.evidence().put(copy.id(), copy);
                    recovered++;
                    break;
                }
            }
        }

        player.analyzedSystems().add(system.id());
        cooldown(player, ActionType.ANALYZE);

        messenger.sendTo(player, Protocol.message(
                MessageType.EVIDENCE_RESULT,
                "server",
                new EvidenceResult(
                        system.id(),
                        entries,
                        "Analizzate " + visible.size() + " voci di log su "
                                + system.name() + ": " + entries.size() + " estratte"
                                + (recovered > 0 ? " (" + recovered + " recuperata)." : ".")
                )
        ));

        messenger.reply(player, ActionType.ANALYZE, true, "Analisi di " + system.name() + " completata.");
    }

    private void isolate(PlayerState player, SystemState system) {
        if (blocked(player, ActionType.ISOLATE, system)) {
            return;
        }

        if (system.isJammed()) {
            messenger.reply(player, ActionType.ISOLATE, false,
                    "Segnale disturbato su " + system.name() + ": impossibile isolarlo ora.");
            return;
        }

        if (system.isIsolated()) {
            player.startCooldown(ActionType.ISOLATE, 10);
            messenger.reply(player, ActionType.ISOLATE, false, system.name() + " è già isolato.");
            return;
        }

        system.isolate(player.clientId(), ISOLATION_SECONDS);
        cooldown(player, ActionType.ISOLATE);

        messenger.sendEvent(new GameEvent(
                "ISOLATION",
                "Sistema isolato",
                player.name() + " ha isolato " + system.name()
                        + " per " + ISOLATION_SECONDS + " secondi.",
                system.id(),
                "INFO"
        ), p -> !p.isInsider() || p == player);

        if (player.isInsider()) {
            session.insiderLog.add(session.clock() + "  Isolamento di copertura su " + system.name());
        }

        messenger.reply(player, ActionType.ISOLATE, true, system.name() + " isolato per " + ISOLATION_SECONDS + " s.");
    }

    private void registerBlock(SystemState system, String reasonText) {
        session.blocked++;

        PlayerState isolator = system.isolatedBy() == null ? null : session.players.get(system.isolatedBy());

        if (isolator != null) {
            isolator.countBlock();
        }

        ServerLog.game("Esfiltrazione bloccata (" + session.blocked + "/" + config.blocksToWin() + "): " + reasonText);

        if (session.blocked >= config.blocksToWin()) {
            outcome.finish(Role.ANALYST,
                    "L'insider è stato contenuto: " + session.blocked
                            + " tentativi di esfiltrazione sono stati bloccati.",
                    null,
                    Map.of());
        }
    }

    private void exfiltrate(PlayerState player, SystemState system) {
        if (blocked(player, ActionType.EXFILTRATE, system)) {
            return;
        }

        cooldown(player, ActionType.EXFILTRATE);

        if (system.isIsolated()) {
            boolean identified = session.random.nextDouble() < exposure(0.7);

            system.append(new LogEntry(session.newEvidence(
                    system,
                    "HIGH",
                    "Connessione in uscita bloccata dall'isolamento: richiesta da "
                            + (identified ? player.workstation() : "host interno non identificato"),
                    identified ? player.name() : null
            ), LogEntry.Kind.TRACE));

            system.raiseAlert(6);
            session.insiderLog.add(session.clock() + "  Esfiltrazione bloccata su " + system.name() + " (isolato)");

            messenger.sendEvent("SIEM_ALERT", "Esfiltrazione bloccata",
                    "L'isolamento di " + system.name() + " ha fermato un trasferimento sospetto.",
                    system.id(), "CRITICAL");

            messenger.reply(player, ActionType.EXFILTRATE, false,
                    "Connessione bloccata: " + system.name() + " è isolato. Potresti aver lasciato una traccia.");

            registerBlock(system, system.name());
            return;
        }

        if (system.isHoneypot()) {
            system.append(new LogEntry(session.newEvidence(
                    system,
                    "CRITICAL",
                    "HONEYPOT: dati esca prelevati da " + player.workstation()
                            + " (account " + player.account() + ")",
                    player.name()
            ), LogEntry.Kind.TRACE));

            system.raiseAlert(10);
            session.insiderLog.add(session.clock() + "  Caduto nell'honeypot di " + system.name());

            messenger.sendEvent("SIEM_ALERT", "Honeypot attivato",
                    "Qualcuno ha prelevato i dati esca da " + system.name() + ". Analizzate i log!",
                    system.id(), "CRITICAL");

            messenger.reply(player, ActionType.EXFILTRATE, false,
                    "I dati di " + system.name() + " erano un'esca: honeypot! Sei stato registrato.");

            registerBlock(system, "honeypot " + system.name());
            return;
        }

        session.exfiltration = Math.min(100, session.exfiltration + config.exfiltrationStep());
        session.lastExfilSystem = system.id();

        boolean silent = system.isJammed();
        boolean identified = false;

        if (!silent) {
            identified = session.random.nextDouble() < exposure(0.5);

            system.append(new LogEntry(session.newEvidence(
                    system,
                    "HIGH",
                    Scenario.exfiltrationText(session.random,
                            identified ? player.workstation() : "host interno non identificato"),
                    identified ? player.name() : null
            ), LogEntry.Kind.TRACE));
        }

        session.insiderLog.add(session.clock() + "  Esfiltrazione da " + system.name()
                + " (" + session.exfiltration + "%)"
                + (silent ? " - sistema disturbato, nessuna traccia" : identified ? " - identità registrata" : ""));

        if (!silent && session.random.nextDouble() < 0.45) {
            system.raiseAlert(8);

            messenger.sendEvent("SIEM_ALERT", "Traffico anomalo",
                    "Il SIEM segnala un picco di traffico in uscita da " + system.name() + ".",
                    system.id(), "WARN");
        }

        messenger.reply(player, ActionType.EXFILTRATE, true,
                (silent ? "Esfiltrazione silenziosa da " : "Esfiltrazione riuscita da ") + system.name()
                        + ": " + session.exfiltration + "% dei dati."
                        + (repeated ? " Ripetere la stessa mossa ti espone." : ""));

        ServerLog.game("Esfiltrazione: " + session.exfiltration + "% (" + system.name() + ")");

        if (session.exfiltration >= 100) {
            outcome.finish(Role.INSIDER,
                    "Esfiltrazione completata: l'insider ha portato fuori tutti i dati.",
                    null,
                    Map.of());
        }
    }

    private void wipe(PlayerState player, SystemState system) {
        if (blocked(player, ActionType.WIPE, system)) {
            return;
        }

        LogEntry target = null;

        for (LogEntry entry : system.log()) {
            if (entry.kind() == LogEntry.Kind.TRACE && !entry.isWiped()) {
                target = entry;
            }
        }

        if (target == null) {
            player.startCooldown(ActionType.WIPE, 5);
            messenger.reply(player, ActionType.WIPE, false,
                    "Nessuna tua traccia da cancellare su " + system.name() + ".");
            return;
        }

        target.setWiped(true);
        cooldown(player, ActionType.WIPE);
        player.countWipe();

        boolean identified = session.random.nextDouble() < exposure(0.3);

        system.append(new LogEntry(session.newEvidence(
                system,
                "MEDIUM",
                "Rotazione manuale dei log di sistema"
                        + (identified ? " eseguita da " + player.workstation() : ""),
                identified ? player.name() : null
        ), LogEntry.Kind.WIPE_MARK));

        session.insiderLog.add(session.clock() + "  Tracce cancellate su " + system.name());

        messenger.reply(player, ActionType.WIPE, true, "Traccia cancellata dai log di " + system.name() + ".");
    }

    private void frame(PlayerState player, SystemState system, String subject) {
        if (blocked(player, ActionType.FRAME, system)) {
            return;
        }

        PlayerState victim = null;

        for (PlayerState other : session.players.values()) {
            if (other != player && other.isActive() && other.name().equals(subject)) {
                victim = other;
            }
        }

        if (victim == null) {
            messenger.reply(player, ActionType.FRAME, false, "Scegli un operatore da incolpare.");
            return;
        }

        system.append(new LogEntry(session.newEvidence(
                system,
                "HIGH",
                Scenario.exfiltrationText(session.random, victim.workstation()),
                victim.name()
        ), LogEntry.Kind.FAKE));

        cooldown(player, ActionType.FRAME);
        player.countFrame();
        session.insiderLog.add(session.clock() + "  Prova falsa contro " + victim.name() + " su " + system.name());

        messenger.reply(player, ActionType.FRAME, true,
                "Prova falsa contro " + victim.name() + " piazzata su " + system.name() + ".");
    }

    private void jam(PlayerState player, SystemState system) {
        if (blocked(player, ActionType.JAM, system)) {
            return;
        }

        if (system.isJammed()) {
            player.startCooldown(ActionType.JAM, 5);
            messenger.reply(player, ActionType.JAM, false, system.name() + " è già disturbato.");
            return;
        }

        system.setJam(JAM_SECONDS);
        cooldown(player, ActionType.JAM);
        player.countJam();

        boolean identified = session.random.nextDouble() < exposure(0.25);

        system.append(new LogEntry(session.newEvidence(
                system,
                "MEDIUM",
                "Interferenza anomala sul segnale di " + system.name()
                        + (identified ? " proveniente da " + player.workstation() : ""),
                identified ? player.name() : null
        ), LogEntry.Kind.TRACE));

        session.insiderLog.add(session.clock() + "  Disturbo del segnale su " + system.name());

        messenger.reply(player, ActionType.JAM, true,
                system.name() + " disturbato per " + JAM_SECONDS
                        + " s: gli analisti non possono leggerlo né isolarlo, e le esfiltrazioni non lasciano tracce.");
    }
}
