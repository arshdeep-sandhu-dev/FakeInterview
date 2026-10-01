package com.shipfast.refund;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.shipfast.MutableClock;
import com.shipfast.ShipFastApp;
import com.shipfast.TestStore;
import com.shipfast.common.Money;
import com.shipfast.common.ValidationException;
import com.shipfast.order.Order;
import com.shipfast.order.OrderRequest;
import com.shipfast.order.OrderRequest.Item;
import com.shipfast.order.OrderStatus;
import com.shipfast.payment.SandboxPaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RefundServiceTest {

    private SandboxPaymentGateway gateway;
    private ShipFastApp app;
    private RefundService refunds;
    private Order order;

    @BeforeEach
    void setUp() {
        MutableClock clock = new MutableClock(TestStore.NOW);
        gateway = new SandboxPaymentGateway(0L, clock);
        app = TestStore.create(gateway, clock);
        refunds = app.refunds();
        order = app.orderService().placeOrder(OrderRequest.of("c-or", new Item("LAMP", 2))); // $80.00
    }

    @Test
    void fullRefundMarksOrderRefunded() {
        refunds.refund(order.getId(), order.getTotal(), "changed mind");

        assertEquals(OrderStatus.REFUNDED, order.getStatus());
        assertEquals(order.getTotal(), order.getRefunded());
    }

    @Test
    void partialRefundThenRemainder() {
        refunds.refund(order.getId(), Money.of("30.00"), "one lamp damaged");
        assertEquals(OrderStatus.PARTIALLY_REFUNDED, order.getStatus());
        assertEquals(Money.of("50.00"), refunds.refundableBalance(order.getId()));

        refunds.refund(order.getId(), refunds.refundableBalance(order.getId()), "second lamp damaged");
        assertEquals(OrderStatus.REFUNDED, order.getStatus());
    }

    @Test
    void refundIsSentToProcessor() {
        refunds.refund(order.getId(), Money.of("12.34"), "price match");
        assertEquals(Money.of("12.34"), gateway.refundedFor(order.getPaymentTransactionId()));
    }

    @Test
    void cannotRefundMoreThanWasPaid() {
        assertThrows(ValidationException.class,
                () -> refunds.refund(order.getId(), Money.of("80.01"), "oops"));
    }

    @Test
    void cannotRefundZero() {
        assertThrows(ValidationException.class, () -> refunds.refund(order.getId(), Money.ZERO, "nothing"));
    }

    @Test
    void cannotRefundAFullyRefundedOrder() {
        refunds.refund(order.getId(), order.getTotal(), "changed mind");
        assertThrows(ValidationException.class, () -> refunds.refund(order.getId(), Money.of("1.00"), "again"));
    }

    @Test
    void cannotRefundDeclinedOrder() {
        Order declined = app.orderService().placeOrder(OrderRequest.of("c-decline", new Item("MUG", 1)));
        assertThrows(ValidationException.class, () -> refunds.refund(declined.getId(), Money.of("1.00"), "n/a"));
    }

    @Test
    void refundsAreAudited() {
        refunds.refund(order.getId(), Money.of("5.00"), "goodwill");
        assertEquals(1, app.audit().entriesWithAction("REFUND").size());
    }
}
