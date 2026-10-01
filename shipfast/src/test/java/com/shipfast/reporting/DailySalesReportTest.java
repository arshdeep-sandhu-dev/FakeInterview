package com.shipfast.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.shipfast.MutableClock;
import com.shipfast.ShipFastApp;
import com.shipfast.TestStore;
import com.shipfast.catalog.Category;
import com.shipfast.common.Money;
import com.shipfast.order.Order;
import com.shipfast.order.OrderRequest;
import com.shipfast.order.OrderRequest.Item;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DailySalesReportTest {

    private static final ZoneId STORE = ShipFastApp.DEFAULT_STORE_ZONE;
    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);

    private MutableClock clock;
    private ShipFastApp app;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(TestStore.NOW);
        app = TestStore.create(clock);
    }

    private Order placeAt(LocalDateTime storeTime, String customer, Item... items) {
        clock.set(storeTime.atZone(STORE).toInstant());
        return app.orderService().placeOrder(OrderRequest.of(customer, items));
    }

    @Test
    void summarisesOrdersPlacedThatDay() {
        Order morning = placeAt(DAY.atTime(9, 15), "c-or", new Item("LAMP", 2));
        Order afternoon = placeAt(DAY.atTime(15, 40), "c-or", new Item("MUG", 1), new Item("TEA", 2));

        SalesSummary summary = app.dailySales().forDate(DAY);

        assertEquals(2, summary.orderCount());
        assertEquals(5, summary.unitsSold());
        assertEquals(morning.getTotal().plus(afternoon.getTotal()), summary.grossSales());
        assertEquals(Money.of("12.00"), summary.salesByCategory().get(Category.GROCERY));
    }

    @Test
    void excludesOrdersFromOtherDays() {
        placeAt(DAY.minusDays(1).atTime(12, 0), "c-or", new Item("MUG", 1));
        placeAt(DAY.atTime(12, 0), "c-or", new Item("MUG", 1));
        placeAt(DAY.plusDays(1).atTime(12, 0), "c-or", new Item("MUG", 1));

        assertEquals(1, app.dailySales().forDate(DAY).orderCount());
    }

    @Test
    void usesStoreTimeZoneNotUtc() {
        // 20:00 in Los Angeles is already the next day in UTC
        placeAt(DAY.atTime(20, 0), "c-or", new Item("MUG", 1));

        assertEquals(1, app.dailySales().forDate(DAY).orderCount());
        assertEquals(0, app.dailySales().forDate(DAY.plusDays(1)).orderCount());
    }

    @Test
    void excludesDeclinedOrders() {
        placeAt(DAY.atTime(10, 0), "c-decline", new Item("MUG", 1));
        assertEquals(0, app.dailySales().forDate(DAY).orderCount());
    }

    @Test
    void netSalesSubtractRefunds() {
        Order order = placeAt(DAY.atTime(10, 0), "c-or", new Item("LAMP", 2));
        app.refunds().refund(order.getId(), Money.of("40.00"), "one returned");

        SalesSummary summary = app.dailySales().forDate(DAY);
        assertEquals(Money.of("80.00"), summary.grossSales());
        assertEquals(Money.of("40.00"), summary.netSales());
    }
}
