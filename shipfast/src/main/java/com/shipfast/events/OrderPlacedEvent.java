package com.shipfast.events;

import com.shipfast.common.Money;
import com.shipfast.customer.LoyaltyTier;
import java.time.Instant;
import java.util.List;

/**
 * Published once an order has been paid for.
 *
 * @param couponCode the coupon the shopper entered, or {@code null} if none
 * @param promotions every promotion that contributed to {@code discount} (coupon codes and
 *                   automatic promotions such as bulk pricing)
 */
public record OrderPlacedEvent(
        String orderId,
        String customerId,
        String customerName,
        String customerEmail,
        LoyaltyTier loyaltyTier,
        List<LineItem> lines,
        Money subtotal,
        Money discount,
        Money tax,
        Money shipping,
        Money total,
        String couponCode,
        List<String> promotions,
        Instant occurredAt) implements DomainEvent {

    public record LineItem(String sku, String name, int quantity, Money unitPrice, Money lineTotal) {
    }

    public Money merchandiseTotal() {
        return subtotal.minus(discount);
    }
}
