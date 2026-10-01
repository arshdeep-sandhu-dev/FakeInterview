package com.shipfast.events;

import java.time.Instant;

public record OrderShippedEvent(
        String orderId,
        String customerEmail,
        String customerName,
        String trackingNumber,
        Instant occurredAt) implements DomainEvent {
}
