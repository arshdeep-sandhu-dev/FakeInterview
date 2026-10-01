package com.shipfast.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.shipfast.MutableClock;
import com.shipfast.ShipFastApp;
import com.shipfast.TestStore;
import com.shipfast.order.OrderRequest;
import com.shipfast.order.OrderRequest.Item;
import com.shipfast.order.OutOfStockException;
import com.shipfast.payment.PaymentUnavailableException;
import com.shipfast.payment.SandboxPaymentGateway;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * {@code coupons.claim(coupon)} in {@link com.shipfast.order.OrderService#placeOrder} takes a use
 * before the order is reserved, charged or settled. Every exit path after that claim must give the
 * use back if the order doesn't go through - a declined card already did; these two didn't
 * (verified: before the surrounding try/catch was added, both left {@code getTimesUsed() == 1}
 * for an order that was never paid).
 */
class CouponClaimRollbackTest {

    private final MutableClock clock = new MutableClock(TestStore.NOW);

    @Test
    void outOfStockGivesTheCouponUseBack() {
        ShipFastApp app = TestStore.create(clock);
        Coupon coupon = Coupon.percentOff("TENOFF", "10", TestStore.NOW.plus(Duration.ofDays(1)));
        app.coupons().register(coupon);

        assertThrows(OutOfStockException.class,
                () -> app.orderService().placeOrder(OrderRequest.of("c-or", new Item("LAMP", 21)).withCoupon("TENOFF")));

        assertEquals(0, coupon.getTimesUsed());
    }

    @Test
    void gatewayUnavailableGivesTheCouponUseBack() {
        SandboxPaymentGateway down = new SandboxPaymentGateway(0L, clock).withConnectTimeoutRate(1.0);
        ShipFastApp app = TestStore.create(down, clock);
        Coupon coupon = Coupon.percentOff("TENOFF2", "10", TestStore.NOW.plus(Duration.ofDays(1)));
        app.coupons().register(coupon);

        assertThrows(PaymentUnavailableException.class,
                () -> app.orderService().placeOrder(OrderRequest.of("c-or", new Item("MUG", 1)).withCoupon("TENOFF2")));

        assertEquals(0, coupon.getTimesUsed());
    }
}
