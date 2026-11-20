package it.soc.server.game;

import it.soc.server.ServerLog;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

public class GameStats {

    private final Path file;
    private final Properties data = new Properties();

    public GameStats(Path file) {
        this.file = file;

        if (file != null && Files.isRegularFile(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                data.load(input);
            } catch (IOException e) {
                ServerLog.warn("Statistiche non leggibili: " + e.getMessage());
            }
        }
    }

    public static GameStats inMemory() {
        return new GameStats(null);
    }

    public synchronized void record(String name, boolean won, int points) {
        String key = key(name);

        data.setProperty(key + ".games", String.valueOf(read(key + ".games") + 1));
        data.setProperty(key + ".wins", String.valueOf(read(key + ".wins") + (won ? 1 : 0)));
        data.setProperty(key + ".points", String.valueOf(read(key + ".points") + points));
    }

    public synchronized void save() {
        if (file == null) {
            return;
        }

        try (OutputStream output = Files.newOutputStream(file)) {
            data.store(output, "SOC: CONTROL ROOM statistiche");
        } catch (IOException e) {
            ServerLog.warn("Statistiche non salvate: " + e.getMessage());
        }
    }

    public synchronized int games(String name) {
        return read(key(name) + ".games");
    }

    public synchronized int wins(String name) {
        return read(key(name) + ".wins");
    }

    public synchronized int points(String name) {
        return read(key(name) + ".points");
    }

    public synchronized String summary(String name) {
        return "Partite " + games(name) + " · Vittorie " + wins(name) + " · Punti " + points(name);
    }

    private int read(String key) {
        try {
            return Integer.parseInt(data.getProperty(key, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String key(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}
