package com.shipfast.events;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventBusTest {

    private static final Instant NOW = Instant.parse("2026-03-10T17:00:00Z");

    private final EventBus bus = new EventBus();
    private final List<String> received = new ArrayList<>();

    private static OrderShippedEvent shipped(String orderId) {
        return new OrderShippedEvent(orderId, "a@example.com", "A", "1Z", NOW);
    }

    @Test
    void deliversToHandlersInSubscriptionOrder() {
        bus.subscribe(OrderShippedEvent.class, e -> received.add("first:" + e.orderId()));
        bus.subscribe(OrderShippedEvent.class, e -> received.add("second:" + e.orderId()));

        bus.publish(shipped("ord-1"));

        assertEquals(List.of("first:ord-1", "second:ord-1"), received);
    }

    @Test
    void onlyDeliversEventsOfTheSubscribedType() {
        bus.subscribe(OrderPlacedEvent.class, e -> received.add("placed"));
        bus.subscribe(OrderShippedEvent.class, e -> received.add("shipped"));

        bus.publish(shipped("ord-1"));

        assertEquals(List.of("shipped"), received);
    }

    @Test
    void supertypeSubscribersSeeEverything() {
        bus.subscribe(DomainEvent.class, e -> received.add(e.getClass().getSimpleName()));

        bus.publish(shipped("ord-1"));

        assertEquals(List.of("OrderShippedEvent"), received);
    }

    @Test
    void publishingWithNoSubscribersIsANoOp() {
        bus.publish(shipped("ord-1"));
        assertEquals(0, bus.subscriberCount());
    }
}
