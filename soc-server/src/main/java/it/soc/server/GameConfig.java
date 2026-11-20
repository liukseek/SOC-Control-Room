package it.soc.server;

public class GameConfig {

    public enum Difficulty {
        EASY("Facile", 8, 1.3, 2, 270),
        NORMAL("Normale", 10, 1.0, 3, 240),
        HARD("Difficile", 12, 0.8, 4, 210);

        private final String label;
        private final int exfiltrationStep;
        private final double insiderCooldown;
        private final int blocksToWin;
        private final int investigationSeconds;

        Difficulty(String label, int exfiltrationStep, double insiderCooldown,
                   int blocksToWin, int investigationSeconds) {
            this.label = label;
            this.exfiltrationStep = exfiltrationStep;
            this.insiderCooldown = insiderCooldown;
            this.blocksToWin = blocksToWin;
            this.investigationSeconds = investigationSeconds;
        }

        public String label() {
            return label;
        }

        public static Difficulty parse(String text) {
            for (Difficulty difficulty : values()) {
                if (difficulty.name().equalsIgnoreCase(text)
                        || difficulty.label.equalsIgnoreCase(text)) {
                    return difficulty;
                }
            }

            return null;
        }
    }

    private volatile int minPlayers = 3;
    private volatile int briefingSeconds = 12;
    private volatile int investigationSeconds = Difficulty.NORMAL.investigationSeconds;
    private volatile int votingSeconds = 60;
    private volatile Difficulty difficulty = Difficulty.NORMAL;

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

    public Difficulty difficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
        this.investigationSeconds = difficulty.investigationSeconds;
    }

    public int exfiltrationStep() {
        return difficulty.exfiltrationStep;
    }

    public int blocksToWin() {
        return difficulty.blocksToWin;
    }

    public int insiderCooldown(int seconds) {
        return Math.max(1, (int) Math.round(seconds * difficulty.insiderCooldown));
    }
}
