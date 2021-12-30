package mindustry.plugin.utils;

import arc.Core;
import arc.func.Cons;

import java.time.Instant;

/** Simple ratelimit */
public class Ratelimit {
    public int eventLimit = 10;
    public int findTime = 1000;
    public Instant begin = Instant.now();
    public int count = 0;

    private boolean noUpdate = false;

    public Ratelimit() {}

    /**
     * The constructor
     * @param eventLimit Event limit
     * @param findTime Time interval in milliseconds
     */
    public Ratelimit(int eventLimit, int findTime) {
        this.eventLimit = eventLimit;
        this.findTime = findTime;
    }

    /**
     * Helper to update begin time
     * @return True if in new interval, false otherwise
     */
    private void updateBegin() {
        if (Instant.now().isAfter(begin.plusMillis(findTime)) && !noUpdate) {
            // new interval
            begin = Instant.now();
            count = 0;
        }
    }

    /**
     * Check and update ratelimit
     * @return True if ratelimit exceeded, false otherwise
     */
    public boolean get() {
        updateBegin();
        count++;
        return count > eventLimit;
    }

    /**
     * Check ratelimit
     * @return True if ratelimit exceeded, false otherwise
     */
    public boolean check() {
        updateBegin();
        return count > eventLimit;
    }

    /** Get number of events in current interval */
    public int events() {
        updateBegin();
        return count;
    }

    /**
     * Provide count next tick. Will inhibit reset
     * @param fn Function to be run
     */
    public void nextTick(Cons<Ratelimit> fn) {
        noUpdate = true;
        Core.app.post(() -> {
            fn.get(this);
            noUpdate = false;
        });
    }
}