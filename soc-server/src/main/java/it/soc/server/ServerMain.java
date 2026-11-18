/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package it.soc.server;

import it.soc.common.Protocol;
import it.soc.server.admin.AdminWindow;
import it.soc.server.game.GameManager;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

/**
 *
 * @author luca
 */
public class ServerMain {
    
    private static final int PORT = Protocol.DEFAULT_PORT;
    private static final ConnectionManager connectionManager = new ConnectionManager();
    private static final GameConfig gameConfig = new GameConfig();
    private static final GameManager gameManager = new GameManager(connectionManager, gameConfig);

    public static void main(String[] args) {

        System.out.println("SOC: CONTROL ROOM");
        System.out.println("Avvio del server...");

        boolean gui = !GraphicsEnvironment.isHeadless()
                && !Arrays.asList(args).contains("--nogui");

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

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            ServerLog.info("Server in ascolto sulla porta " + PORT);

            while (true) {
    Socket clientSocket = serverSocket.accept();

    ServerLog.info(
            "Nuovo client connesso: "
            + clientSocket.getInetAddress()
    );

    ClientHandler handler = new ClientHandler(
        clientSocket,
        connectionManager,
        gameManager
    );
    Thread clientThread = new Thread(handler);

    clientThread.start();
    ServerLog.info(
        "Client attualmente connessi: "
        + connectionManager.getClientCount()
);
}

        } catch (IOException e) {
            ServerLog.error("Errore durante l'avvio del server: " + e.getMessage());
        }
    }
}
