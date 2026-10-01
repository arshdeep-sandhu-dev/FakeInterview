package com.shipfast.events;

@FunctionalInterface
public interface EventHandler<E extends DomainEvent> {

    void handle(E event);
}
