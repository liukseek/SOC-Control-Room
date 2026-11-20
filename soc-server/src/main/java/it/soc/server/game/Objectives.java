package it.soc.server.game;

import java.util.ArrayList;
import java.util.List;

final class Objectives {

    static final int ANALYZE_TARGET = 3;
    static final int SHARE_TARGET = 2;
    static final int BLOCK_TARGET = 1;
    static final int WIPE_TARGET = 2;
    static final int FRAME_TARGET = 1;
    static final int JAM_TARGET = 1;

    private Objectives() {
    }

    static List<String> describe(PlayerState player, int exfiltration) {
        List<String> list = new ArrayList<>();

        if (player.isInsider()) {
            list.add(line(exfiltration >= 100, "Esfiltra i dati fino al 100%", exfiltration, 100));
            list.add(line(player.wipes() >= WIPE_TARGET, "Cancella le tue tracce", player.wipes(), WIPE_TARGET));
            list.add(line(player.frames() >= FRAME_TARGET, "Depista un collega", player.frames(), FRAME_TARGET));
            list.add(line(player.jams() >= JAM_TARGET, "Disturba un sistema", player.jams(), JAM_TARGET));
            return list;
        }

        list.add(line(player.analyzedSystems().size() >= ANALYZE_TARGET,
                "Analizza sistemi diversi", player.analyzedSystems().size(), ANALYZE_TARGET));
        list.add(line(player.shared() >= SHARE_TARGET,
                "Condividi prove verificate", player.shared(), SHARE_TARGET));
        list.add(line(player.blocks() >= BLOCK_TARGET,
                "Blocca un'esfiltrazione isolando", player.blocks(), BLOCK_TARGET));
        return list;
    }

    static int completed(PlayerState player) {
        int count = 0;

        if (player.isInsider()) {
            if (player.wipes() >= WIPE_TARGET) {
                count++;
            }

            if (player.frames() >= FRAME_TARGET) {
                count++;
            }

            if (player.jams() >= JAM_TARGET) {
                count++;
            }

            return count;
        }

        if (player.analyzedSystems().size() >= ANALYZE_TARGET) {
            count++;
        }

        if (player.shared() >= SHARE_TARGET) {
            count++;
        }

        if (player.blocks() >= BLOCK_TARGET) {
            count++;
        }

        return count;
    }

    private static String line(boolean done, String text, int value, int target) {
        return (done ? "[x] " : "[ ] ") + text + " (" + Math.min(value, target) + "/" + target + ")";
    }
}
