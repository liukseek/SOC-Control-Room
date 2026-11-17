
package it.soc.client;

import it.soc.common.Message;
import it.soc.common.MessageType;
import it.soc.common.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class NetworkClient {

    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;

    private Thread receiverThread;

    private volatile boolean connected = false;

    private Consumer<Message> messageListener;
    private Consumer<String> disconnectListener;

    public void setMessageListener(Consumer<Message> listener) {
        this.messageListener = listener;
    }

    public void setDisconnectListener(Consumer<String> listener) {
        this.disconnectListener = listener;
    }

    public void connect(String host, int port) throws IOException {

        if (connected) {
            throw new IllegalStateException("Client già connesso.");
        }

        socket = new Socket(host, port);

        input = new BufferedReader(
                new InputStreamReader(
                        socket.getInputStream(),
                        StandardCharsets.UTF_8
                )
        );

        output = new PrintWriter(
                socket.getOutputStream(),
                true,
                StandardCharsets.UTF_8
        );

        connected = true;

        startReceiver();
    }

    public synchronized void sendMessage(Message message)
            throws IOException {

        if (!connected || output == null) {
            throw new IOException("Client non connesso.");
        }

        output.println(Protocol.encode(message));

        if (output.checkError()) {
            throw new IOException("Errore durante l'invio del messaggio.");
        }
    }

    private void startReceiver() {

        receiverThread = new Thread(() -> {

            try {
                String received;

                while (connected && (received = input.readLine()) != null) {

                    Message message = Protocol.decode(received);

                    Consumer<Message> listener = messageListener;

                    if (listener != null) {
                        listener.accept(message);
                    }
                }

            } catch (IOException | IllegalArgumentException e) {

                if (connected) {
                    notifyDisconnect(e.getMessage());
                }

            } finally {
                close();
            }

        }, "SOC-Client-Receiver");

        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    public boolean isConnected() {
        return connected && socket != null && socket.isConnected()
                && !socket.isClosed();
    }

    private void notifyDisconnect(String reason) {
        Consumer<String> listener = disconnectListener;

        if (listener != null) {
            listener.accept(reason);
        }
    }

    public synchronized void close() {

        if (!connected && socket == null) {
            return;
        }

        connected = false;

        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Errore chiusura socket: " + e.getMessage());
        }

        socket = null;
        input = null;
        output = null;
    }
}
