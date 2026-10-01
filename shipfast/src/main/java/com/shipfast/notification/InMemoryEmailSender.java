package com.shipfast.notification;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Captures outgoing email instead of sending it. Used in development, tests and simulations. */
public class InMemoryEmailSender implements EmailSender {

    private final List<Email> outbox = new CopyOnWriteArrayList<>();

    @Override
    public void send(Email email) {
        outbox.add(email);
    }

    public List<Email> sent() {
        return List.copyOf(outbox);
    }

    public List<Email> sentTo(String address) {
        return outbox.stream().filter(email -> email.to().equals(address)).toList();
    }

    public List<Email> forOrder(String orderId) {
        return outbox.stream().filter(email -> orderId.equals(email.orderId())).toList();
    }
}
