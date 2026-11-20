package it.soc.common;

import static org.junit.jupiter.api.Assertions.*;

import it.soc.common.model.ActionType;
import it.soc.common.model.ChatMessage;
import it.soc.common.model.GameResult;
import it.soc.common.model.GameState;
import it.soc.common.model.LobbyState;
import it.soc.common.model.Phase;
import it.soc.common.model.PlayerAction;
import it.soc.common.model.PlayerInfo;
import it.soc.common.model.Role;
import it.soc.common.model.SystemNode;
import it.soc.common.model.SystemStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

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
                List.of(new PlayerInfo("Operator01", "WS-01", false, true, false)),
                Map.of(ActionType.ANALYZE, 4),
                1,
                3,
                List.of("[ ] Analizza 3 sistemi (1/3)"),
                true,
                1,
                2,
                false
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

    @Test
    void testCallVoteMessageType() {
        Message message = new Message(MessageType.CALL_VOTE, "Operator01", "");

        assertEquals(message, Protocol.decode(Protocol.encode(message)));
    }

    @Test
    void testWhisperChatPayload() {
        ChatMessage chat = new ChatMessage("Operator01", "ciao", null, "02:41:10", "Operator02");

        assertEquals(chat, Protocol.fromContent(Protocol.toContent(chat), ChatMessage.class));
    }

    @Test
    void testGameResultPayload() {
        GameResult result = new GameResult(
                Role.ANALYST,
                "L'insider è stato identificato.",
                "Operator03",
                "Operator03",
                Map.of("Operator03", 2),
                40,
                9,
                3,
                List.of("02:40:11  Esfiltrazione da DB"),
                Map.of("Operator01", 150),
                Map.of("Operator01", "Vittorie 1 · Partite 1")
        );

        assertEquals(result, Protocol.fromContent(Protocol.toContent(result), GameResult.class));
    }

    @Test
    void testJamActionPayload() {
        PlayerAction action = new PlayerAction(ActionType.JAM, "FW", null);

        assertEquals(action, Protocol.fromContent(Protocol.toContent(action), PlayerAction.class));
    }
}
