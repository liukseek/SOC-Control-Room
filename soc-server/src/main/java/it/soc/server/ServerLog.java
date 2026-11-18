package it.soc.server;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

public final class ServerLog {

    public static final String INFO = "INFO";
    public static final String GAME = "GAME";
    public static final String WARN = "WARN";
    public static final String ERROR = "ERROR";

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final List<BiConsumer<String, String>> LISTENERS =
            new CopyOnWriteArrayList<>();

    private ServerLog() {
    }

    public static void addListener(BiConsumer<String, String> listener) {
        LISTENERS.add(listener);
    }

    public static void info(String text) {
        write(INFO, text);
    }

    public static void game(String text) {
        write(GAME, text);
    }

    public static void warn(String text) {
        write(WARN, text);
    }

    public static void error(String text) {
        write(ERROR, text);
    }

    private static void write(String level, String text) {
        String line = "[" + LocalTime.now().format(TIME) + "] " + text;

        if (ERROR.equals(level)) {
            System.err.println(line);
        } else {
            System.out.println(line);
        }

        for (BiConsumer<String, String> listener : LISTENERS) {
            listener.accept(level, line);
        }
    }
}
