package com.shipfast.events;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-process publish/subscribe. Handlers are invoked synchronously on the publishing thread, in
 * the order they subscribed.
 */
public class EventBus {

    private record Subscription<E extends DomainEvent>(Class<E> type, EventHandler<? super E> handler) {

        boolean accepts(DomainEvent event) {
            return type.isInstance(event);
        }

        void deliver(DomainEvent event) {
            handler.handle(type.cast(event));
        }
    }

    private final List<Subscription<?>> subscriptions = new CopyOnWriteArrayList<>();

    public <E extends DomainEvent> void subscribe(Class<E> type, EventHandler<? super E> handler) {
        subscriptions.add(new Subscription<>(type, handler));
    }

    public void publish(DomainEvent event) {
        for (Subscription<?> subscription : subscriptions) {
            if (subscription.accepts(event)) {
                subscription.deliver(event);
            }
        }
    }

    public int subscriberCount() {
        return subscriptions.size();
    }
}
