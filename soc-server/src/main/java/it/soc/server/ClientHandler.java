
package it.soc.server;

import it.soc.common.Message;
import it.soc.common.MessageType;
import it.soc.common.Protocol;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final ConnectionManager connectionManager;
    private final String clientId;

    private volatile PrintWriter output;

    public ClientHandler(Socket socket, ConnectionManager connectionManager) {
        this.socket = socket;
        this.connectionManager = connectionManager;
        this.clientId = connectionManager.register(this);

        System.out.println("Client registrato: " + clientId);
    }

    @Override
    public void run() {

        try (
            BufferedReader input = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
            );
            PrintWriter writer = new PrintWriter(
                    socket.getOutputStream(), true
            )
        ) {
            output = writer;

            String received;

            while ((received = input.readLine()) != null) {

                Message message = Protocol.decode(received);

                System.out.println(
                        "Messaggio ricevuto: " + message.type()
                );

                switch (message.type()) {

                    case CONNECT -> sendMessage(new Message(
                            MessageType.PONG,
                            "server",
                            "Connessione stabilita con SOC: CONTROL ROOM"
                    ));

                    case JOIN_LOBBY -> {
                        String name = message.content().trim();

                        if (name.isBlank() || name.length() > 20) {
                            sendMessage(new Message(
                                    MessageType.ERROR,
                                    "server",
                                    "Nome non valido."
                            ));
                            break;
                        }

                        connectionManager.setPlayerName(clientId, name);

                        System.out.println(
                                "Giocatore entrato nella lobby: " + name
                        );

                        connectionManager.broadcast(new Message(
                                MessageType.LOBBY_UPDATE,
                                "server",
                                String.join(", ", connectionManager.getPlayerNames())
                        ));
                    }

                    case DISCONNECT -> {
                        return;
                    }

                    default -> System.out.println(
                            "Messaggio non gestito: " + message.type()
                    );
                }
            }

        } catch (IOException | IllegalArgumentException e) {
            System.err.println(
                    "Connessione client terminata: " + e.getMessage()
            );
        } finally {
            connectionManager.unregister(clientId);

            connectionManager.broadcast(new Message(
                    MessageType.LOBBY_UPDATE,
                    "server",
                    String.join(", ", connectionManager.getPlayerNames())
            ));

            System.out.println("Client disconnesso: " + clientId);

            try {
                socket.close();
            } catch (IOException e) {
                System.err.println(
                        "Errore chiusura socket: " + e.getMessage()
                );
            }
        }
    }

    // Invia un messaggio a questo client
    public synchronized void sendMessage(Message message) {

        PrintWriter writer = output;

        if (writer != null) {
            writer.println(Protocol.encode(message));
        }
    }
}
