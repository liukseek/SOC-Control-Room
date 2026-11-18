/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package it.soc.server;

import it.soc.common.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ConnectionManager {

    private final ConcurrentHashMap<String, ClientHandler> clients =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, String> playerNames =
            new ConcurrentHashMap<>();

    private final CopyOnWriteArrayList<String> joinOrder =
            new CopyOnWriteArrayList<>();

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
        joinOrder.remove(clientId);
    }

    // Registra il nome del giocatore
    public void setPlayerName(String clientId, String name) {
        if (clients.containsKey(clientId)) {
            playerNames.put(clientId, name);
            joinOrder.addIfAbsent(clientId);
        }
    }

    // Restituisce il nome di un giocatore
    public String getPlayerName(String clientId) {
        return playerNames.get(clientId);
    }

    // Restituisce la lista dei nomi dei giocatori
    public List<String> getPlayerNames() {
        List<String> names = new ArrayList<>();

        for (String clientId : joinOrder) {
            String name = playerNames.get(clientId);

            if (name != null) {
                names.add(name);
            }
        }

        return List.copyOf(names);
    }

    public List<String> getLobbyClientIds() {
        List<String> ids = new ArrayList<>();

        for (String clientId : joinOrder) {
            if (playerNames.containsKey(clientId)) {
                ids.add(clientId);
            }
        }

        return List.copyOf(ids);
    }

    public String getHostId() {
        List<String> ids = getLobbyClientIds();
        return ids.isEmpty() ? null : ids.get(0);
    }

    public String getHostName() {
        String hostId = getHostId();
        return hostId == null ? null : playerNames.get(hostId);
    }

    public boolean isNameTaken(String name, String clientId) {
        for (var entry : playerNames.entrySet()) {
            if (!entry.getKey().equals(clientId)
                    && entry.getValue().equalsIgnoreCase(name)) {
                return true;
            }
        }

        return false;
    }

    // Restituisce il numero di client connessi
    public int getClientCount() {
        return clients.size();
    }

    // Restituisce gli ID dei client connessi
    public List<String> getClientIds() {
        return List.copyOf(clients.keySet());
    }

    public void sendTo(String clientId, Message message) {
        ClientHandler handler = clients.get(clientId);

        if (handler != null) {
            handler.sendMessage(message);
        }
    }
    
    // Invia un messaggio a tutti i client connessi
    public void broadcast(it.soc.common.Message message) {

        for (ClientHandler client : clients.values()) {
            client.sendMessage(message);
        }
    }

}
