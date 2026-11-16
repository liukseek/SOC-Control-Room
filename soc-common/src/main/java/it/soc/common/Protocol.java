/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package it.soc.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 *
 * @author luca
 */
public final class Protocol {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
}
