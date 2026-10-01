package com.shipfast.notification;

import com.shipfast.events.EventHandler;
import com.shipfast.events.OrderShippedEvent;

public class ShipmentEmailListener implements EventHandler<OrderShippedEvent> {

    private final EmailSender sender;

    public ShipmentEmailListener(EmailSender sender) {
        this.sender = sender;
    }

    @Override
    public void handle(OrderShippedEvent event) {
        String body = "Hi " + event.customerName() + ",\n\n"
                + "Good news - order " + event.orderId() + " is on its way.\n"
                + "Tracking number: " + event.trackingNumber() + "\n";
        sender.send(new Email(event.customerEmail(), "Your order has shipped", body, event.orderId()));
    }
}
