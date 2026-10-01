package com.shipfast.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shipfast.MutableClock;
import com.shipfast.ShipFastApp;
import com.shipfast.TestStore;
import com.shipfast.common.Money;
import com.shipfast.common.ValidationException;
import com.shipfast.inventory.StockSnapshot;
import com.shipfast.notification.Email;
import com.shipfast.order.OrderRequest.Item;
import com.shipfast.payment.SandboxPaymentGateway;
import com.shipfast.pricing.Coupon;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderServiceTest {

    private MutableClock clock;
    private SandboxPaymentGateway gateway;
    private ShipFastApp app;
    private OrderService orders;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(TestStore.NOW);
        gateway = new SandboxPaymentGateway(0L, clock);
        app = TestStore.create(gateway, clock);
        orders = app.orderService();
    }

    @Test
    void placesOrderAndCapturesPayment() {
        Order order = orders.placeOrder(OrderRequest.of("c-or", new Item("MUG", 2)));

        assertEquals(OrderStatus.PAID, order.getStatus());
        // 25.00 merchandise, no Oregon tax, 800g shipping = 5.74
        assertEquals(Money.of("30.74"), order.getTotal());
        assertEquals(TestStore.NOW, order.getPlacedAt());

        List<SandboxPaymentGateway.Capture> captures = gateway.capturesFor(order.getId());
        assertEquals(1, captures.size());
        assertEquals(order.getTotal(), captures.get(0).amount());
        assertEquals(order.getPaymentTransactionId(), captures.get(0).transactionId());
    }

    @Test
    void paidOrderStockIsSold() {
        orders.placeOrder(OrderRequest.of("c-or", new Item("MUG", 2)));
        assertEquals(new StockSnapshot("MUG", 100, 98, 0, 2), app.inventory().snapshot("MUG"));
    }

    @Test
    void sendsConfirmationEmail() {
        Order order = orders.placeOrder(OrderRequest.of("c-or", new Item("MUG", 2)));

        List<Email> emails = app.email().forOrder(order.getId());
        assertEquals(1, emails.size());
        assertEquals("olive@example.com", emails.get(0).to());
        assertTrue(emails.get(0).body().contains("$30.74"));
    }

    @Test
    void awardsLoyaltyPointsOnMerchandiseOnly() {
        orders.placeOrder(OrderRequest.of("c-ca", new Item("LAMP", 1)));
        // GOLD = 3 points per whole dollar of merchandise (tax & shipping excluded)
        assertEquals(120, app.loyalty().balance("c-ca"));
    }

    @Test
    void writesAuditEntry() {
        Order order = orders.placeOrder(OrderRequest.of("c-or", new Item("MUG", 1)));
        assertEquals(1, app.audit().entriesFor(order.getId()).size());
    }

    @Test
    void declinedPaymentReleasesStock() {
        Order order = orders.placeOrder(OrderRequest.of("c-decline", new Item("MUG", 2)));

        assertEquals(OrderStatus.PAYMENT_DECLINED, order.getStatus());
        assertEquals(new StockSnapshot("MUG", 100, 100, 0, 0), app.inventory().snapshot("MUG"));
        assertTrue(app.email().forOrder(order.getId()).isEmpty());
    }

    @Test
    void outOfStockOrderIsRejectedWithoutCharging() {
        assertThrows(OutOfStockException.class, () -> orders.placeOrder(OrderRequest.of("c-or", new Item("LAMP", 21))));
        assertEquals(20, app.inventory().available("LAMP"));
        assertTrue(gateway.captures().isEmpty());
    }

    @Test
    void duplicateSkusAreMergedIntoOneLine() {
        Order order = orders.placeOrder(OrderRequest.of("c-or", new Item("MUG", 1), new Item("MUG", 2)));
        assertEquals(1, order.getLines().size());
        assertEquals(3, order.getLines().get(0).quantity());
    }

    @Test
    void couponIsAppliedAndRedeemed() {
        Coupon welcome = Coupon.percentOff("WELCOME10", "10", TestStore.NOW.plus(Duration.ofDays(30)));
        app.coupons().register(welcome);

        Order order = orders.placeOrder(OrderRequest.of("c-or", new Item("LAMP", 2)).withCoupon("welcome10"));

        assertEquals(Money.of("8.00"), order.getDiscount());
        assertEquals("WELCOME10", order.getCouponCode());
        assertEquals(1, welcome.getTimesUsed());
    }

    @Test
    void couponIsNotRedeemedWhenPaymentDeclined() {
        Coupon welcome = Coupon.percentOff("WELCOME10", "10", TestStore.NOW.plus(Duration.ofDays(30)));
        app.coupons().register(welcome);

        orders.placeOrder(OrderRequest.of("c-decline", new Item("LAMP", 1)).withCoupon("WELCOME10"));

        assertEquals(0, welcome.getTimesUsed());
    }

    @Test
    void rejectsUnknownProducts() {
        assertThrows(ValidationException.class, () -> orders.placeOrder(OrderRequest.of("c-or", new Item("NOPE", 1))));
    }

    @Test
    void rejectsDiscontinuedProducts() {
        app.catalog().discontinue("MUG");
        assertThrows(ValidationException.class, () -> orders.placeOrder(OrderRequest.of("c-or", new Item("MUG", 1))));
    }

    @Test
    void shippingAnOrderSendsTrackingEmail() {
        Order order = orders.placeOrder(OrderRequest.of("c-or", new Item("MUG", 1)));
        orders.ship(order.getId(), "1Z999");

        assertEquals(OrderStatus.SHIPPED, order.getStatus());
        assertEquals(2, app.email().forOrder(order.getId()).size());
    }

    @Test
    void lifetimeSpendIsNetOfRefunds() {
        Order order = orders.placeOrder(OrderRequest.of("c-or", new Item("LAMP", 2)));
        app.refunds().refund(order.getId(), Money.of("20.00"), "goodwill");
        assertEquals(Money.of("60.00"), orders.lifetimeSpend("c-or"));
    }
}
