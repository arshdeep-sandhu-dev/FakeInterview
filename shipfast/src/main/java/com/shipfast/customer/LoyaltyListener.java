package com.shipfast.customer;

import com.shipfast.events.EventHandler;
import com.shipfast.events.OrderPlacedEvent;

/** Credits loyalty points when an order is placed. Tax and shipping do not earn points. */
public class LoyaltyListener implements EventHandler<OrderPlacedEvent> {

    private final LoyaltyService loyalty;

    public LoyaltyListener(LoyaltyService loyalty) {
        this.loyalty = loyalty;
    }

    @Override
    public void handle(OrderPlacedEvent event) {
        loyalty.award(event.customerId(), event.merchandiseTotal(), event.loyaltyTier());
    }
}
