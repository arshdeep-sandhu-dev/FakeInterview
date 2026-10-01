package com.shipfast.payment;

import java.time.Duration;

/** Abstraction over {@link Thread#sleep} so that tests don't have to wait for backoff. */
@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration);

    static Sleeper system() {
        return duration -> {
            try {
                Thread.sleep(duration.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while backing off", e);
            }
        };
    }

    static Sleeper none() {
        return duration -> { };
    }
}
