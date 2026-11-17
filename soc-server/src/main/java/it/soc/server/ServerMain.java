/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package it.soc.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 *
 * @author luca
 */
public class ServerMain {
    
    private static final int PORT = 1234;
    private static final ConnectionManager connectionManager = new ConnectionManager();

    public static void main(String[] args) {

        System.out.println("SOC: CONTROL ROOM");
        System.out.println("Avvio del server...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            System.out.println("Server in ascolto sulla porta " + PORT);

            while (true) {
    Socket clientSocket = serverSocket.accept();

    System.out.println(
            "Nuovo client connesso: "
            + clientSocket.getInetAddress()
    );

    ClientHandler handler = new ClientHandler(
        clientSocket,
        connectionManager
    );
    Thread clientThread = new Thread(handler);

    clientThread.start();
    System.out.println(
        "Client attualmente connessi: "
        + connectionManager.getClientCount()
);
}

        } catch (IOException e) {
            System.err.println("Errore durante l'avvio del server: " + e.getMessage());
        }
    }
}
