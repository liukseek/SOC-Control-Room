package it.soc.server.game;

import it.soc.common.Message;
import it.soc.common.MessageType;
import it.soc.common.Protocol;
import it.soc.common.model.ChatMessage;
import it.soc.common.model.Evidence;
import it.soc.common.model.Phase;

class TeamChannel {

    private static final int MAX_LENGTH = 200;
    private static final String WHISPER = "/w ";

    private final GameSession session;
    private final GameMessenger messenger;

    TeamChannel(GameSession session, GameMessenger messenger) {
        this.session = session;
        this.messenger = messenger;
    }

    void share(String clientId, String evidenceId) {
        PlayerState player = session.players.get(clientId);

        if (player == null || player.isExpelled()
                || (session.phase != Phase.INVESTIGATION && session.phase != Phase.VOTING)) {
            return;
        }

        Evidence evidence = player.evidence().get(evidenceId);

        if (evidence == null) {
            messenger.reply(player, null, false, "Puoi condividere solo prove che hai raccolto.");
            return;
        }

        if (!session.sharedEvidence.add(evidenceId)) {
            messenger.reply(player, null, false, "Questa prova è già stata condivisa.");
            return;
        }

        player.countShared();

        messenger.broadcast(Protocol.message(
                MessageType.CHAT,
                "server",
                new ChatMessage(player.name(), "ha condiviso una prova verificata", evidence, session.clock(), null)
        ));

        messenger.sendState(player);
    }

    void chat(String clientId, String text) {
        PlayerState player = session.players.get(clientId);

        if (player == null || session.phase == Phase.LOBBY || text == null) {
            return;
        }

        String clean = text.strip();

        if (clean.isEmpty()) {
            return;
        }

        if (clean.length() > MAX_LENGTH) {
            clean = clean.substring(0, MAX_LENGTH);
        }

        long now = System.currentTimeMillis();

        if (now - player.lastChat() < 500) {
            return;
        }

        player.setLastChat(now);

        if (clean.regionMatches(true, 0, WHISPER, 0, WHISPER.length())) {
            whisper(player, clean.substring(WHISPER.length()).strip());
            return;
        }

        messenger.broadcast(Protocol.message(
                MessageType.CHAT,
                "server",
                new ChatMessage(player.name(), clean, null, session.clock(), null)
        ));
    }

    private void whisper(PlayerState from, String rest) {
        PlayerState target = null;

        for (PlayerState other : session.players.values()) {
            String name = other.name();

            boolean matches = other != from
                    && other.isConnected()
                    && rest.regionMatches(true, 0, name, 0, name.length())
                    && (rest.length() == name.length() || rest.charAt(name.length()) == ' ');

            if (matches && (target == null || name.length() > target.name().length())) {
                target = other;
            }
        }

        if (target == null) {
            messenger.reply(from, null, false, "Uso: /w Nome messaggio (operatore connesso, diverso da te).");
            return;
        }

        String body = rest.substring(target.name().length()).strip();

        if (body.isEmpty()) {
            messenger.reply(from, null, false, "Scrivi un messaggio dopo il nome.");
            return;
        }

        Message message = Protocol.message(
                MessageType.CHAT,
                "server",
                new ChatMessage(from.name(), body, null, session.clock(), target.name())
        );

        messenger.sendTo(from, message);
        messenger.sendTo(target, message);
    }
}
