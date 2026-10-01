package com.shipfast.payment;

import java.time.Duration;

/**
 * Exponential backoff for transient gateway failures.
 *
 * @param maxAttempts total attempts including the first one
 */
public record RetryPolicy(int maxAttempts, Duration initialBackoff, double multiplier, Duration maxBackoff) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
    }

    public static RetryPolicy defaults() {
        return new RetryPolicy(3, Duration.ofMillis(200), 2.0, Duration.ofSeconds(2));
    }

    public static RetryPolicy noRetries() {
        return new RetryPolicy(1, Duration.ZERO, 1.0, Duration.ZERO);
    }

    /** How long to wait before making attempt number {@code attempt} (1-based). */
    public Duration backoffBefore(int attempt) {
        if (attempt <= 1) {
            return Duration.ZERO;
        }
        long millis = (long) (initialBackoff.toMillis() * Math.pow(multiplier, attempt - 2));
        return Duration.ofMillis(Math.min(millis, maxBackoff.toMillis()));
    }
}
