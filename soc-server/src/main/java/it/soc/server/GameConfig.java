package it.soc.server;

public class GameConfig {

    private volatile int minPlayers = 3;
    private volatile int briefingSeconds = 12;
    private volatile int investigationSeconds = 240;
    private volatile int votingSeconds = 60;

    public int minPlayers() {
        return minPlayers;
    }

    public void setMinPlayers(int minPlayers) {
        this.minPlayers = Math.max(2, minPlayers);
    }

    public int briefingSeconds() {
        return briefingSeconds;
    }

    public void setBriefingSeconds(int briefingSeconds) {
        this.briefingSeconds = Math.max(3, briefingSeconds);
    }

    public int investigationSeconds() {
        return investigationSeconds;
    }

    public void setInvestigationSeconds(int investigationSeconds) {
        this.investigationSeconds = Math.max(30, investigationSeconds);
    }

    public int votingSeconds() {
        return votingSeconds;
    }

    public void setVotingSeconds(int votingSeconds) {
        this.votingSeconds = Math.max(15, votingSeconds);
    }
}
