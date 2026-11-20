package it.soc.server;

public class MessageRateLimiter {

    private final int limit;
    private final long windowMillis;

    private long windowStart;
    private int count;
    private int strikes;

    public MessageRateLimiter(int limit, long windowMillis) {
        this.limit = limit;
        this.windowMillis = windowMillis;
    }

    public boolean allow(long now) {
        if (now - windowStart >= windowMillis) {
            windowStart = now;
            count = 0;
        }

        count++;

        if (count <= limit) {
            return true;
        }

        strikes++;
        return false;
    }

    public int strikes() {
        return strikes;
    }
}
