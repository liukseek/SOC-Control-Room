/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package it.soc.server;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionManager {

    private final ConcurrentHashMap<String, ClientHandler> clients =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, String> playerNames =
            new ConcurrentHashMap<>();

    // Registra un client e gli assegna un ID univoco
    public String register(ClientHandler handler) {

        String clientId = UUID.randomUUID().toString();

        clients.put(clientId, handler);

        return clientId;
    }

    // Rimuove un client e il relativo nome
    public void unregister(String clientId) {
        clients.remove(clientId);
        playerNames.remove(clientId);
    }

    // Registra il nome del giocatore
    public void setPlayerName(String clientId, String name) {
        if (clients.containsKey(clientId)) {
            playerNames.put(clientId, name);
        }
    }

    // Restituisce il nome di un giocatore
    public String getPlayerName(String clientId) {
        return playerNames.get(clientId);
    }

    // Restituisce la lista dei nomi dei giocatori
    public List<String> getPlayerNames() {
        return List.copyOf(playerNames.values());
    }

    // Restituisce il numero di client connessi
    public int getClientCount() {
        return clients.size();
    }

    // Restituisce gli ID dei client connessi
    public List<String> getClientIds() {
        return List.copyOf(clients.keySet());
    }
    
    // Invia un messaggio a tutti i client connessi
    public void broadcast(it.soc.common.Message message) {

        for (ClientHandler client : clients.values()) {
            client.sendMessage(message);
        }
    }

}
