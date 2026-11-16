package it.soc.common;

public enum MessageType {

    CONNECT,
    DISCONNECT,

    JOIN_LOBBY,
    LOBBY_UPDATE,

    ROLE_ASSIGN,
    GAME_START,
    GAME_UPDATE,
    GAME_OVER,

    REQUEST_EVIDENCE,
    SUBMIT_VOTE,

    ERROR,
    PING,
    PONG
}
