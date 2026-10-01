package com.shipfast.notification;

import com.shipfast.events.EventHandler;
import com.shipfast.events.OrderPlacedEvent;

/** Sends the order confirmation / receipt email. */
public class ConfirmationEmailListener implements EventHandler<OrderPlacedEvent> {

    private final EmailSender sender;

    public ConfirmationEmailListener(EmailSender sender) {
        this.sender = sender;
    }

    @Override
    public void handle(OrderPlacedEvent event) {
        StringBuilder body = new StringBuilder()
                .append("Hi ").append(event.customerName()).append(",\n\n")
                .append("Thanks for shopping with ShipFast! Your order ").append(event.orderId())
                .append(" is confirmed.\n\n");
        for (OrderPlacedEvent.LineItem line : event.lines()) {
            body.append(String.format("  %-30s x%-4d %10s%n", line.name(), line.quantity(), line.lineTotal()));
        }
        body.append('\n');
        row(body, "Subtotal", event.subtotal().toString());
        if (event.discount().isPositive()) {
            row(body, "Discount (" + String.join(", ", event.promotions()) + ")", "-" + event.discount());
        }
        row(body, "Tax", event.tax().toString());
        row(body, "Shipping", event.shipping().isZero() ? "FREE" : event.shipping().toString());
        row(body, "Total charged", event.total().toString());
        body.append("\nWe'll email you again when it ships.\n");

        sender.send(new Email(
                event.customerEmail(),
                "Your ShipFast order " + event.orderId() + " is confirmed",
                body.toString(),
                event.orderId()));
    }

    private static void row(StringBuilder body, String label, String value) {
        body.append(String.format("  %-36s %10s%n", label, value));
    }
}
