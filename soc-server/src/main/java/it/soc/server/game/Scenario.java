package it.soc.server.game;

import it.soc.common.model.Role;
import it.soc.common.model.RoleInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class Scenario {

    private Scenario() {
    }

    public static List<SystemState> createSystems() {
        List<SystemState> systems = new ArrayList<>();

        systems.add(new SystemState("FW", "FW-EDGE01", "Firewall perimetrale"));
        systems.add(new SystemState("VPN", "VPN-GW01", "Gateway VPN"));
        systems.add(new SystemState("MAIL", "MAIL-SRV01", "Server di posta"));
        systems.add(new SystemState("FILE", "FILE-SRV02", "File server condiviso"));
        systems.add(new SystemState("DB", "SRV-DB01", "Database clienti"));
        systems.add(new SystemState("HR", "HR-PORTAL", "Portale risorse umane"));

        return systems;
    }

    public static RoleInfo roleInfo(Role role, String workstation) {
        if (role == Role.INSIDER) {
            return new RoleInfo(
                    role,
                    workstation,
                    "INSIDER",
                    "Sei l'infiltrato. Porta fuori i dati dell'azienda senza "
                    + "farti scoprire: gli altri operatori ti credono un "
                    + "analista come loro.",
                    List.of(
                            "Esfiltra dati dai sistemi fino al 100%",
                            "Cancella le tue tracce dai log",
                            "Depista le indagini incolpando altri operatori",
                            "Disturba un sistema per esfiltrare senza lasciare tracce",
                            "Sopravvivi al voto finale"
                    )
            );
        }

        if (role == Role.FORENSIC) {
            return new RoleInfo(
                    role,
                    workstation,
                    "FORENSE",
                    "Sei l'analista forense del SOC. Le tue analisi estraggono più voci "
                    + "di log e possono recuperare dal disco le tracce che l'insider "
                    + "ha cancellato. Condividi quello che trovi con il team.",
                    List.of(
                            "Analizza i log: estrai più voci degli altri analisti",
                            "Recupera le tracce cancellate dall'insider",
                            "Condividi le prove verificate con il team",
                            "Vota per identificare l'insider"
                    )
            );
        }

        return new RoleInfo(
                role,
                workstation,
                "SOC ANALYST",
                "Qualcuno all'interno della rete sta sottraendo dati riservati. "
                + "Analizza i log, raccogli prove e smaschera l'insider prima "
                + "che l'esfiltrazione sia completata.",
                List.of(
                        "Analizza i log dei sistemi per raccogliere prove",
                        "Isola i sistemi sospetti per bloccare l'esfiltrazione",
                        "Condividi le prove verificate con il team",
                        "Vota per identificare l'insider"
                )
        );
    }

    public static String externalIp(Random random) {
        int[] prefixes = {185, 91, 45, 203, 177, 62};

        return prefixes[random.nextInt(prefixes.length)] + "."
                + (10 + random.nextInt(240)) + "."
                + random.nextInt(255) + "."
                + (2 + random.nextInt(250));
    }

    public static Noise noise(Random random, SystemState system, PlayerState player) {
        String ws = player.workstation();
        String acct = player.account();

        return switch (system.id()) {
            case "FW" -> pick(random,
                    new Noise("INFO", "Regola di inoltro applicata al traffico da " + ws, player),
                    new Noise("MEDIUM", "Scansione porte bloccata (origine esterna "
                            + externalIp(random) + ")", null),
                    new Noise("INFO", "Firmware: controllo integrità completato", null),
                    new Noise("LOW", "Connessione HTTPS consentita da " + ws, player)
            );
            case "VPN" -> pick(random,
                    new Noise("INFO", "Sessione VPN avviata dall'account " + acct, player),
                    new Noise("MEDIUM", "Tentativo di login VPN fallito per " + acct, player),
                    new Noise("INFO", "Tunnel VPN rinegoziato correttamente", null),
                    new Noise("LOW", "Sessione VPN chiusa per inattività (" + ws + ")", player)
            );
            case "MAIL" -> pick(random,
                    new Noise("INFO", "Email inviata da " + acct + " con allegato ("
                            + (1 + random.nextInt(9)) + "." + random.nextInt(10) + " MB)", player),
                    new Noise("LOW", "Filtro antispam: 3 messaggi in quarantena", null),
                    new Noise("MEDIUM", "Regola di inoltro automatico creata da " + acct, player),
                    new Noise("INFO", "Accesso webmail da " + ws, player)
            );
            case "FILE" -> pick(random,
                    new Noise("INFO", "Accesso alla cartella /progetti da " + ws, player),
                    new Noise("LOW", "Backup incrementale eseguito", null),
                    new Noise("MEDIUM", "Download di " + (20 + random.nextInt(80))
                            + " file da /contratti da " + ws, player),
                    new Noise("INFO", "Permessi aggiornati su /condivisa da " + acct, player)
            );
            case "DB" -> pick(random,
                    new Noise("INFO", "Query di lettura su tabella clienti da " + ws, player),
                    new Noise("MEDIUM", "Esportazione report mensile da " + acct, player),
                    new Noise("LOW", "Indici del database ricostruiti", null),
                    new Noise("INFO", "Connessione applicativa aperta da " + ws, player)
            );
            default -> pick(random,
                    new Noise("INFO", "Consultazione buste paga da " + acct, player),
                    new Noise("LOW", "Certificato TLS rinnovato", null),
                    new Noise("MEDIUM", "Modifica anagrafica dipendente da " + ws, player),
                    new Noise("INFO", "Login al portale HR da " + ws, player)
            );
        };
    }

    public static String exfiltrationText(Random random, String origin) {
        int size = 80 + random.nextInt(420);
        String ip = externalIp(random);

        return switch (random.nextInt(3)) {
            case 0 -> "Trasferimento anomalo di " + size + " MB verso " + ip + " da " + origin;
            case 1 -> "Archivio cifrato (" + size + " MB) caricato su " + ip + " da " + origin;
            default -> "Tunnel DNS sospetto verso " + ip + " aperto da " + origin;
        };
    }

    @SafeVarargs
    private static <T> T pick(Random random, T... options) {
        return options[random.nextInt(options.length)];
    }

    public record Noise(String severity, String text, PlayerState origin) {}
}
