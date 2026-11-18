
package it.soc.server;

import it.soc.common.Message;
import it.soc.common.MessageType;
import it.soc.common.Protocol;
import it.soc.common.model.PlayerAction;
import it.soc.server.game.GameManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final ConnectionManager connectionManager;
    private final GameManager gameManager;
    private final String clientId;

    private volatile PrintWriter output;

    public ClientHandler(Socket socket, ConnectionManager connectionManager,
                         GameManager gameManager) {
        this.socket = socket;
        this.connectionManager = connectionManager;
        this.gameManager = gameManager;
        this.clientId = connectionManager.register(this);

        ServerLog.info("Client registrato: " + clientId);
    }

    @Override
    public void run() {

        try (
            BufferedReader input = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
            );
            PrintWriter writer = new PrintWriter(
                    socket.getOutputStream(), true, StandardCharsets.UTF_8
            )
        ) {
            output = writer;

            String received;

            while ((received = input.readLine()) != null) {

                Message message;

                try {
                    message = Protocol.decode(received);
                } catch (IllegalArgumentException e) {
                    ServerLog.warn("Messaggio non valido da " + clientId);
                    continue;
                }

                if (message.type() == null) {
                    continue;
                }

                if (isLogged(message.type())) {
                    ServerLog.info("Messaggio ricevuto: " + message.type()
                            + describeSender());
                }

                try {
                    if (!handle(message)) {
                        return;
                    }
                } catch (IllegalArgumentException e) {
                    sendMessage(new Message(
                            MessageType.ERROR,
                            "server",
                            "Richiesta non valida."
                    ));
                }
            }

        } catch (IOException e) {
            ServerLog.warn(
                    "Connessione client terminata: " + e.getMessage()
            );
        } finally {
            String name = connectionManager.getPlayerName(clientId);

            connectionManager.unregister(clientId);
            gameManager.playerLeft(clientId);
            gameManager.broadcastLobby();

            ServerLog.info("Client disconnesso: "
                    + (name == null ? clientId : name));

            try {
                socket.close();
            } catch (IOException e) {
                ServerLog.error(
                        "Errore chiusura socket: " + e.getMessage()
                );
            }
        }
    }

    private boolean handle(Message message) {
        String content = message.content() == null ? "" : message.content();

        switch (message.type()) {

            case CONNECT -> sendMessage(new Message(
                    MessageType.PONG,
                    "server",
                    "Connessione stabilita con SOC: CONTROL ROOM"
            ));

            case PING -> sendMessage(new Message(
                    MessageType.PONG,
                    "server",
                    "pong"
            ));

            case JOIN_LOBBY -> {
                String name = content.trim();

                if (name.isBlank() || name.length() > 20) {
                    sendMessage(new Message(
                            MessageType.ERROR,
                            "server",
                            "Nome non valido."
                    ));
                    break;
                }

                if (connectionManager.isNameTaken(name, clientId)) {
                    sendMessage(new Message(
                            MessageType.ERROR,
                            "server",
                            "Nickname già in uso."
                    ));
                    break;
                }

                String current = connectionManager.getPlayerName(clientId);

                if (gameManager.isInGame() && current == null) {
                    sendMessage(new Message(
                            MessageType.ERROR,
                            "server",
                            "Partita in corso, riprova tra qualche minuto."
                    ));
                    break;
                }

                connectionManager.setPlayerName(clientId, name);

                ServerLog.info(
                        "Giocatore entrato nella lobby: " + name
                );

                gameManager.broadcastLobby();
            }

            case START_GAME -> {
                String error = gameManager.startGame(clientId);

                if (error != null) {
                    sendMessage(new Message(MessageType.ERROR, "server", error));
                }
            }

            case PLAYER_ACTION -> gameManager.handleAction(
                    clientId, Protocol.fromContent(content, PlayerAction.class)
            );

            case REQUEST_EVIDENCE -> gameManager.handleEvidenceRequest(clientId, content);

            case SHARE_EVIDENCE -> gameManager.handleShare(clientId, content);

            case CHAT -> gameManager.handleChat(clientId, content);

            case SUBMIT_VOTE -> gameManager.handleVote(clientId, content);

            case DISCONNECT -> {
                return false;
            }

            default -> ServerLog.warn(
                    "Messaggio non gestito: " + message.type()
            );
        }

        return true;
    }

    private static boolean isLogged(MessageType type) {
        return switch (type) {
            case PING, CHAT, PLAYER_ACTION, REQUEST_EVIDENCE, SHARE_EVIDENCE, SUBMIT_VOTE -> false;
            default -> true;
        };
    }

    private String describeSender() {
        String name = connectionManager.getPlayerName(clientId);
        return name == null ? "" : " da " + name;
    }

    // Invia un messaggio a questo client
    public synchronized void sendMessage(Message message) {

        PrintWriter writer = output;

        if (writer != null) {
            writer.println(Protocol.encode(message));
        }
    }
}
