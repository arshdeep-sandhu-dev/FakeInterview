package com.shipfast.events;

import java.time.Instant;

/** Marker for facts that other parts of the system may react to. */
public interface DomainEvent {

    Instant occurredAt();
}
