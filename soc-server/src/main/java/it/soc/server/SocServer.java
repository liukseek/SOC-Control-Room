package it.soc.server;

import it.soc.common.Message;
import it.soc.common.MessageType;
import it.soc.server.game.GameManager;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SocServer {

    private final int port;
    private final ConnectionManager connectionManager;
    private final GameManager gameManager;

    private volatile ServerSocket serverSocket;
    private volatile boolean running;

    public SocServer(int port, ConnectionManager connectionManager, GameManager gameManager) {
        this.port = port;
        this.connectionManager = connectionManager;
        this.gameManager = gameManager;
    }

    public void run() {
        try (ServerSocket socket = new ServerSocket(port)) {
            serverSocket = socket;
            running = true;

            ServerLog.info("Server in ascolto sulla porta " + port);

            while (running) {
                Socket clientSocket = socket.accept();

                ServerLog.info("Nuovo client connesso: " + clientSocket.getInetAddress());

                ClientHandler handler = new ClientHandler(clientSocket, connectionManager, gameManager);

                Thread clientThread = new Thread(handler);
                clientThread.setDaemon(true);
                clientThread.start();

                ServerLog.info("Client attualmente connessi: " + connectionManager.getClientCount());
            }
        } catch (IOException e) {
            if (running) {
                ServerLog.error("Errore durante l'avvio del server: " + e.getMessage());
            }
        } finally {
            running = false;
        }
    }

    public void shutdown() {
        boolean wasRunning = running;
        running = false;

        ServerLog.info("Arresto del server in corso...");

        gameManager.stopGame();

        connectionManager.broadcast(new Message(
                MessageType.ERROR,
                "server",
                "Il server si sta arrestando."
        ));

        connectionManager.closeAll();
        gameManager.shutdown();

        ServerSocket socket = serverSocket;

        if (wasRunning && socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                ServerLog.error("Errore chiusura server: " + e.getMessage());
            }
        }

        ServerLog.info("Server arrestato.");
    }
}
