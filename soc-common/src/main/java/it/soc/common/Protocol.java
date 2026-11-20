package it.soc.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class Protocol {
    
    public static final int DEFAULT_PORT = 1234;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public Protocol() {
    }
    
    public static String encode(Message message){
        try{
            return MAPPER.writeValueAsString(message);
        }catch(JsonProcessingException Ex){
            throw new IllegalArgumentException(
                    "ERRORE CODIFICA MESSAGGIO\n", Ex
            );
        }
    }
    
    public static Message decode(String json) {
        try {
            return MAPPER.readValue(json, Message.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Errore nella decodifica del messaggio", e
            );
        }
    }

    public static String toContent(Object payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Errore nella codifica del contenuto", e
            );
        }
    }

    public static <T> T fromContent(String content, Class<T> type) {
        try {
            return MAPPER.readValue(content, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Errore nella decodifica del contenuto", e
            );
        }
    }

    public static Message message(MessageType type, String sender, Object payload) {
        return new Message(type, sender, toContent(payload));
    }
}
