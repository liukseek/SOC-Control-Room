/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package it.soc.common;

import static org.junit.jupiter.api.Assertions.*;

import it.soc.common.model.ActionType;
import it.soc.common.model.GameState;
import it.soc.common.model.LobbyState;
import it.soc.common.model.Phase;
import it.soc.common.model.PlayerAction;
import it.soc.common.model.PlayerInfo;
import it.soc.common.model.SystemNode;
import it.soc.common.model.SystemStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 *
 * @author luca
 */
public class ProtocolTest {
      
    @Test
    void testMessageEncodingAndDecoding() {

        Message original = new Message(
                MessageType.PING,
                "client-1",
                "Test connection"
        );

        String json = Protocol.encode(original);

        Message decoded = Protocol.decode(json);

        assertEquals(original, decoded);
    }

    @Test
    void testLobbyStatePayload() {
        LobbyState state = new LobbyState(
                List.of("Operator01", "Operator02"),
                "Operator01",
                3,
                false
        );

        Message message = Protocol.message(MessageType.LOBBY_UPDATE, "server", state);
        Message decoded = Protocol.decode(Protocol.encode(message));

        assertEquals(state, Protocol.fromContent(decoded.content(), LobbyState.class));
    }

    @Test
    void testGameStatePayload() {
        GameState state = new GameState(
                Phase.INVESTIGATION,
                120,
                240,
                32,
                List.of(new SystemNode("DB", "SRV-DB01", "Database", SystemStatus.ISOLATED, true, 7)),
                List.of(new PlayerInfo("Operator01", "WS-01", false, true)),
                Map.of(ActionType.ANALYZE, 4)
        );

        String content = Protocol.toContent(state);

        assertEquals(state, Protocol.fromContent(content, GameState.class));
    }

    @Test
    void testPlayerActionPayload() {
        PlayerAction action = new PlayerAction(ActionType.FRAME, "MAIL", "Operator02");

        assertEquals(action, Protocol.fromContent(Protocol.toContent(action), PlayerAction.class));
    }

    @Test
    void testInvalidContentIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Protocol.fromContent("{non json", LobbyState.class));
    }
}
