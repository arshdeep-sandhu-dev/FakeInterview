package com.shipfast.refund;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shipfast.MutableClock;
import com.shipfast.ShipFastApp;
import com.shipfast.TestStore;
import com.shipfast.catalog.Category;
import com.shipfast.catalog.Product;
import com.shipfast.common.Money;
import com.shipfast.order.Order;
import com.shipfast.order.OrderRequest;
import com.shipfast.order.OrderRequest.Item;
import com.shipfast.order.OrderStatus;
import org.junit.jupiter.api.Test;

/**
 * SUP-108: a refund for the full amount must mark the order {@code REFUNDED}, even when the
 * refund amount was typed or submitted with a different number of decimal digits than the order's
 * stored total - as long as they're the same dollar amount.
 *
 * <p>{@code Money.equals()} is backed by {@code BigDecimal.equals()}, which treats mathematically
 * equal values with different scales as unequal ({@code new BigDecimal("25.50").equals(new
 * BigDecimal("25.5"))} is {@code false}, even though {@code compareTo} says they're equal). This
 * reproduces the ticket's exact numbers: an order totalling $25.50 (scale 2, from real catalog
 * pricing), refunded with the same amount typed as "25.5" (scale 1) - the way a UI that drops
 * trailing zeros from a round number would send it.
 */
class RefundScaleTest {

    @Test
    void sameAmountWithFewerDecimalDigitsStillCountsAsAFullRefund() {
        MutableClock clock = new MutableClock(TestStore.NOW);
        ShipFastApp app = TestStore.create(clock);
        // $20.51 (<=500g, so shipping is just the $4.99 base rate, no per-half-kilo add-on) with no
        // tax (Oregon) gives a $25.50 total - the exact figure from the ticket.
        app.catalog().add(new Product("SCALE-TEST", "Scale Test Item", Category.HOME, Money.of("20.51"), 100));
        app.inventory().receive("SCALE-TEST", 10);

        Order order = app.orderService().placeOrder(OrderRequest.of("c-or", new Item("SCALE-TEST", 1)));
        assertEquals(Money.of("25.50"), order.getTotal(), "test setup should produce the ticket's $25.50 total");

        // Scale 2 total ("25.50") refunded with a scale 1 amount ("25.5") - same value, fewer digits.
        app.refunds().refund(order.getId(), Money.of("25.5"), "refund in full");

        assertEquals(OrderStatus.REFUNDED, order.getStatus(),
                "25.5 and 25.50 are the same amount, so this should be a full refund, not " + order.getStatus());
        assertTrue(app.refunds().refundableBalance(order.getId()).isZero(),
                "nothing should be left to refund");
    }
}
