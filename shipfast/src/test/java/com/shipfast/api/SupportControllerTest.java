package com.shipfast.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shipfast.ShipFastApp;
import com.shipfast.TestStore;
import com.shipfast.order.Order;
import com.shipfast.order.OrderRequest;
import com.shipfast.order.OrderRequest.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupportControllerTest {

    private ShipFastApp app;
    private Order order;

    @BeforeEach
    void setUp() {
        app = TestStore.create();
        order = app.orderService().placeOrder(OrderRequest.of("c-or", new Item("LAMP", 2))); // $80.00
    }

    @Test
    void showsOrderDetails() {
        ApiResponse response = app.support().orderDetails(order.getId());
        assertEquals(200, response.status());
        assertTrue(response.body().contains("total=$80.00"));
    }

    @Test
    void refundsPartOfAnOrder() {
        ApiResponse response = app.support().refund(order.getId(), "15.00", "late delivery");
        assertEquals(200, response.status());
        assertTrue(response.body().contains("PARTIALLY_REFUNDED"));
    }

    @Test
    void rejectsMalformedAmounts() {
        assertEquals(400, app.support().refund(order.getId(), "ten dollars", "typo").status());
    }

    @Test
    void unknownOrderIs404() {
        assertEquals(404, app.support().refund("ord-nope", "1.00", "n/a").status());
        assertEquals(404, app.support().orderDetails("ord-nope").status());
    }
}
