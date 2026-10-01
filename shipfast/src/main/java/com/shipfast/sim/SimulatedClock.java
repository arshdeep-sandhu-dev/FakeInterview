package com.shipfast.sim;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.function.Supplier;

/**
 * A clock the simulator controls. Each simulated shopper runs "at" its own arrival time on
 * whichever worker thread picks it up, so the clock supports a per-thread override on top of a
 * global time.
 */
public final class SimulatedClock extends Clock {

    private final ZoneId zone;
    private final ThreadLocal<Instant> threadTime = new ThreadLocal<>();
    private volatile Instant globalTime;

    public SimulatedClock(Instant start, ZoneId zone) {
        this.globalTime = start;
        this.zone = zone;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("SimulatedClock is fixed to " + this.zone);
    }

    @Override
    public Instant instant() {
        Instant local = threadTime.get();
        return local != null ? local : globalTime;
    }

    public void set(Instant time) {
        this.globalTime = time;
    }

    public <T> T runAt(Instant time, Supplier<T> action) {
        threadTime.set(time);
        try {
            return action.get();
        } finally {
            threadTime.remove();
        }
    }
}
