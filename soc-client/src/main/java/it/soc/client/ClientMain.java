/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package it.soc.client;

import it.soc.common.Message;
import it.soc.common.MessageType;
import it.soc.common.Protocol;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientMain {

    private static final String HOST = "localhost";
    private static final int PORT = 1234;

    public static void main(String[] args) {

        try (
            Socket socket = new Socket(HOST, PORT);
            BufferedReader input = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
            );
            PrintWriter output = new PrintWriter(
                    socket.getOutputStream(), true
            );
            BufferedReader keyboard = new BufferedReader(
                    new InputStreamReader(System.in)
            )
        ) {
            System.out.println("================================");
            System.out.println("       SOC: CONTROL ROOM");
            System.out.println("================================");

            output.println(Protocol.encode(new Message(
                    MessageType.CONNECT,
                    "client",
                    "Richiesta di connessione"
            )));

            String response = input.readLine();

            if (response != null) {
                Message message = Protocol.decode(response);
                System.out.println(message.content());
            }

            System.out.print("\nScegli il tuo nickname: ");
            String nickname = keyboard.readLine();

            output.println(Protocol.encode(new Message(
                    MessageType.JOIN_LOBBY,
                    "client",
                    nickname
            )));

            System.out.println("\nSei entrato nella lobby!");
            System.out.println("Scrivi 'exit' per uscire.");

            Thread receiver = new Thread(() -> {
                try {
                    String received;

                    while ((received = input.readLine()) != null) {
                        Message message = Protocol.decode(received);

                        if (message.type() == MessageType.LOBBY_UPDATE) {
                            System.out.println(
                                    "\nGiocatori nella lobby: "
                                    + message.content()
                            );
                        }
                    }

                } catch (IOException e) {
                    System.err.println("Connessione terminata.");
                }
            });

            receiver.setDaemon(true);
            receiver.start();

            String command;

            while ((command = keyboard.readLine()) != null) {

                if (command.equalsIgnoreCase("exit")) {
                    output.println(Protocol.encode(new Message(
                            MessageType.DISCONNECT,
                            nickname,
                            ""
                    )));
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println("Errore client: " + e.getMessage());
        }
    }
}
