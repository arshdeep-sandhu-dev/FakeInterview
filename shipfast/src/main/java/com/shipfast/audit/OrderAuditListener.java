package com.shipfast.audit;

import com.shipfast.events.EventHandler;
import com.shipfast.events.OrderPlacedEvent;
import java.util.Locale;

/** Writes an audit entry for every placed order, including which promotion was used. */
public class OrderAuditListener implements EventHandler<OrderPlacedEvent> {

    private final AuditLog audit;

    public OrderAuditListener(AuditLog audit) {
        this.audit = audit;
    }

    @Override
    public void handle(OrderPlacedEvent event) {
        StringBuilder detail = new StringBuilder()
                .append("customer=").append(event.customerId())
                .append(" items=").append(event.lines().size())
                .append(" total=").append(event.total());
        if (event.discount().isPositive()) {
            detail.append(" discount=").append(event.discount())
                    .append(" promo=").append(event.couponCode().toUpperCase(Locale.ROOT));
        }
        audit.record("ORDER_PLACED", event.orderId(), detail.toString());
    }
}
