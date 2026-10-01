package com.shipfast.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shipfast.MutableClock;
import com.shipfast.ShipFastApp;
import com.shipfast.TestStore;
import com.shipfast.order.OrderRequest;
import com.shipfast.order.OrderRequest.Item;
import com.shipfast.payment.SandboxPaymentGateway;
import org.junit.jupiter.api.Test;

class CheckoutControllerTest {

    private final MutableClock clock = new MutableClock(TestStore.NOW);

    @Test
    void returns201WithOrderIdOnSuccess() {
        ShipFastApp app = TestStore.create(clock);
        ApiResponse response = app.checkout().placeOrder(OrderRequest.of("c-or", new Item("MUG", 1)));

        assertEquals(201, response.status());
        assertTrue(app.orders().find(response.orderId()).isPresent());
    }

    @Test
    void returns402WhenCardDeclined() {
        ShipFastApp app = TestStore.create(clock);
        ApiResponse response = app.checkout().placeOrder(OrderRequest.of("c-decline", new Item("MUG", 1)));
        assertEquals(402, response.status());
    }

    @Test
    void returns409WhenOutOfStock() {
        ShipFastApp app = TestStore.create(clock);
        ApiResponse response = app.checkout().placeOrder(OrderRequest.of("c-or", new Item("LAMP", 99)));
        assertEquals(409, response.status());
    }

    @Test
    void returns400ForUnknownCoupon() {
        ShipFastApp app = TestStore.create(clock);
        ApiResponse response = app.checkout().placeOrder(
                OrderRequest.of("c-or", new Item("MUG", 1)).withCoupon("BOGUS"));
        assertEquals(400, response.status());
    }

    @Test
    void returns400ForUnknownCustomer() {
        ShipFastApp app = TestStore.create(clock);
        ApiResponse response = app.checkout().placeOrder(OrderRequest.of("c-nobody", new Item("MUG", 1)));
        assertEquals(400, response.status());
    }

    @Test
    void returns503WhenPaymentProcessorIsDown() {
        SandboxPaymentGateway down = new SandboxPaymentGateway(0L, clock).withConnectTimeoutRate(1.0);
        ShipFastApp app = TestStore.create(down, clock);

        ApiResponse response = app.checkout().placeOrder(OrderRequest.of("c-or", new Item("MUG", 1)));

        assertEquals(503, response.status());
        assertTrue(down.captures().isEmpty());
    }

    @Test
    void successfulCheckoutsLeaveNoServerErrors() {
        ShipFastApp app = TestStore.create(clock);
        app.checkout().placeOrder(OrderRequest.of("c-or", new Item("MUG", 1)));
        app.checkout().placeOrder(OrderRequest.of("c-ca", new Item("LAMP", 1), new Item("TEA", 3)));
        assertTrue(app.checkout().serverErrors().isEmpty());
    }
}
