package it.soc.server;

import it.soc.common.Protocol;
import it.soc.server.admin.AdminWindow;
import it.soc.server.game.GameManager;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;

public class ServerMain {

    private static final int PORT = Protocol.DEFAULT_PORT;
    private static final ConnectionManager connectionManager = new ConnectionManager();
    private static final GameConfig gameConfig = new GameConfig();
    private static final GameManager gameManager = new GameManager(connectionManager, gameConfig);
    private static final SocServer server = new SocServer(PORT, connectionManager, gameManager);

    public static void main(String[] args) {

        System.out.println("SOC: CONTROL ROOM");
        System.out.println("Avvio del server...");

        boolean gui = !GraphicsEnvironment.isHeadless()
                && !Arrays.asList(args).contains("--nogui");

        for (String arg : args) {
            if (arg.startsWith("--difficulty=")) {
                GameConfig.Difficulty difficulty = GameConfig.Difficulty.parse(arg.substring(13));

                if (difficulty == null) {
                    System.err.println("Difficoltà non valida: " + arg.substring(13)
                            + " (usa easy, normal o hard)");
                    return;
                }

                gameConfig.setDifficulty(difficulty);
            }
        }

        for (String arg : args) {
            if (arg.startsWith("--min=")) {
                gameConfig.setMinPlayers(Integer.parseInt(arg.substring(6)));
            } else if (arg.startsWith("--investigation=")) {
                gameConfig.setInvestigationSeconds(Integer.parseInt(arg.substring(16)));
            } else if (arg.startsWith("--voting=")) {
                gameConfig.setVotingSeconds(Integer.parseInt(arg.substring(9)));
            }
        }

        if (gui) {
            AdminWindow.open(gameManager, connectionManager, PORT);
        }

        server.run();
    }

    public static void shutdown() {
        server.shutdown();
    }
}
