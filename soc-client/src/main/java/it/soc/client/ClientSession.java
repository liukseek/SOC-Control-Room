package it.soc.client;

import it.soc.common.Message;
import it.soc.common.MessageType;
import it.soc.common.Protocol;
import it.soc.common.model.LobbyState;
import it.soc.common.model.RoleInfo;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.util.function.Consumer;

public class ClientSession {

    private final NetworkClient networkClient;

    private volatile Consumer<Message> handler;
    private volatile Consumer<String> disconnectHandler;

    private String nickname;
    private LobbyState lobby;
    private RoleInfo role;

    public ClientSession(NetworkClient networkClient) {
        this.networkClient = networkClient;

        networkClient.setMessageListener(message ->
                SwingUtilities.invokeLater(() -> dispatch(message))
        );

        networkClient.setDisconnectListener(reason ->
                SwingUtilities.invokeLater(() -> {
                    Consumer<String> current = disconnectHandler;

                    if (current != null) {
                        current.accept(reason);
                    }
                })
        );
    }

    private void dispatch(Message message) {
        if (message.type() == MessageType.LOBBY_UPDATE) {
            try {
                lobby = Protocol.fromContent(message.content(), LobbyState.class);
            } catch (IllegalArgumentException e) {
                System.err.println("Lobby non valida: " + e.getMessage());
            }
        }

        if (message.type() == MessageType.ROLE_ASSIGN) {
            try {
                role = Protocol.fromContent(message.content(), RoleInfo.class);
            } catch (IllegalArgumentException e) {
                System.err.println("Ruolo non valido: " + e.getMessage());
            }
        }

        Consumer<Message> current = handler;

        if (current != null) {
            current.accept(message);
        }
    }

    public void setHandler(Consumer<Message> handler) {
        this.handler = handler;
    }

    public void setDisconnectHandler(Consumer<String> disconnectHandler) {
        this.disconnectHandler = disconnectHandler;
    }

    public NetworkClient network() {
        return networkClient;
    }

    public String nickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public LobbyState lobby() {
        return lobby;
    }

    public RoleInfo role() {
        return role;
    }

    public boolean send(MessageType type, String content) {
        try {
            networkClient.sendMessage(new Message(type, nickname, content));
            return true;
        } catch (IOException e) {
            System.err.println("Invio non riuscito: " + e.getMessage());
            return false;
        }
    }

    public boolean send(MessageType type, Object payload) {
        return send(type, Protocol.toContent(payload));
    }

    public void close() {
        networkClient.close();
    }
}
