package it.soc.server;

import it.soc.common.Message;

public interface MessageSink {

    void sendMessage(Message message);

    void close();
}
