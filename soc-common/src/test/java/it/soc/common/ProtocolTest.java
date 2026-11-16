/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package it.soc.common;

import static org.junit.jupiter.api.Assertions.*;
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
}
