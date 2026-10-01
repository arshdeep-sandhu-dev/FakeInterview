package com.shipfast.common;

import java.util.concurrent.atomic.AtomicLong;

/** Generates human-readable, process-unique identifiers such as {@code ord-1042}. */
public class IdGenerator {

    private final AtomicLong sequence;

    public IdGenerator() {
        this(1000);
    }

    public IdGenerator(long start) {
        this.sequence = new AtomicLong(start);
    }

    public String next(String prefix) {
        return prefix + "-" + sequence.incrementAndGet();
    }
}
