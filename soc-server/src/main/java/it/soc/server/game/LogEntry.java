package it.soc.server.game;

import it.soc.common.model.Evidence;

public class LogEntry {

    public enum Kind {
        NOISE,
        TRACE,
        FAKE,
        WIPE_MARK
    }

    private final Evidence evidence;
    private final Kind kind;
    private boolean wiped;

    public LogEntry(Evidence evidence, Kind kind) {
        this.evidence = evidence;
        this.kind = kind;
    }

    public Evidence evidence() {
        return evidence;
    }

    public Kind kind() {
        return kind;
    }

    public boolean isWiped() {
        return wiped;
    }

    public void setWiped(boolean wiped) {
        this.wiped = wiped;
    }

    public boolean isSuspicious() {
        return "HIGH".equals(evidence.severity())
                || "CRITICAL".equals(evidence.severity());
    }
}
