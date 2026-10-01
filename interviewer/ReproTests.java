// Reference reproduction + edge-case tests for every ticket (SPOILERS).
// Usage: copy to shipfast/src/test/java/com/shipfast/repro/ReproTests.java, then
//   .\test.ps1 --select-class com.shipfast.repro.ReproTests
// On the buggy baseline 15 of 19 fail. The 4 that pass (differentOrdersGetDifferentKeys,
// midnightBelongsToNextDayOnly, fallBackDayHas25Hours, springForwardDayHas23Hours) are
// guard tests that catch plausible-but-wrong fixes. All 19 pass with reference-fix.patch.
package com.shipfast.repro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shipfast.MutableClock;
import com.shipfast.ShipFastApp;
import com.shipfast.TestStore;
import com.shipfast.common.IdGenerator;
import com.shipfast.common.Money;
import com.shipfast.events.EventBus;
import com.shipfast.events.OrderShippedEvent;
import com.shipfast.inventory.InventoryService;
import com.shipfast.inventory.StockSnapshot;
import com.shipfast.order.Order;
import com.shipfast.order.OrderRequest;
import com.shipfast.order.OrderRequest.Item;
import com.shipfast.order.OrderStatus;
import com.shipfast.order.OutOfStockException;
import com.shipfast.payment.ChargeRequest;
import com.shipfast.payment.ChargeResponse;
import com.shipfast.payment.GatewayTimeoutException;
import com.shipfast.payment.PaymentGateway;
import com.shipfast.payment.PaymentOutcome;
import com.shipfast.payment.PaymentService;
import com.shipfast.payment.PaymentUnavailableException;
import com.shipfast.payment.RetryPolicy;
import com.shipfast.payment.SandboxPaymentGateway;
import com.shipfast.payment.Sleeper;
import com.shipfast.pricing.Coupon;
import com.shipfast.pricing.CouponRejectedException;
import com.shipfast.pricing.CouponService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReproTests {

    static final ZoneId STORE = ShipFastApp.DEFAULT_STORE_ZONE;

    // ------------------------------------------------------------------ SUP-101 coupon over-use
    @Nested
    class T1_Coupon {
        final MutableClock clock = new MutableClock(TestStore.NOW);
        final CouponService coupons = new CouponService(clock);

        @Test
        void couponCanBeUsedExactlyMaxUsesTimes() {
            Coupon c = Coupon.amountOff("FIRST3", "5", Money.ZERO, TestStore.NOW.plus(Duration.ofDays(1)), 3);
            coupons.register(c);
            for (int i = 0; i < 3; i++) {
                coupons.validate("FIRST3", Money.of("20.00"));
                coupons.redeem(c);
            }
            assertThrows(CouponRejectedException.class, () -> coupons.validate("FIRST3", Money.of("20.00")));
        }

        @Test
        void singleUseCoupon() {
            Coupon c = Coupon.amountOff("ONCE", "5", Money.ZERO, TestStore.NOW.plus(Duration.ofDays(1)), 1);
            coupons.register(c);
            coupons.validate("ONCE", Money.of("20.00"));
            coupons.redeem(c);
            assertThrows(CouponRejectedException.class, () -> coupons.validate("ONCE", Money.of("20.00")));
        }

        @Test
        void concurrentCheckoutsNeverExceedLimit() throws Exception {
            MutableClock clk = new MutableClock(TestStore.NOW);
            SandboxPaymentGateway gw = new SandboxPaymentGateway(0L, clk).withMaxLatency(Duration.ofMillis(5));
            ShipFastApp app = TestStore.create(gw, clk);
            Coupon c = Coupon.amountOff("FIRST5", "5", Money.ZERO, TestStore.NOW.plus(Duration.ofDays(1)), 5);
            app.coupons().register(c);
            runConcurrently(32, () -> app.checkout().placeOrder(OrderRequest.of("c-or", new Item("MUG", 1)).withCoupon("FIRST5")));
            long paidWithCoupon = app.orders().findAll().stream()
                    .filter(o -> o.getStatus() == OrderStatus.PAID && "FIRST5".equals(o.getCouponCode())).count();
            assertTrue(paidWithCoupon <= 5, "paid with coupon: " + paidWithCoupon);
        }
    }

    // ------------------------------------------------------------------ SUP-102 double charge
    @Nested
    class T2_DoubleCharge {
        @Test
        void lostResponseRetryDoesNotChargeTwice() {
            SandboxPaymentGateway sandbox = new SandboxPaymentGateway();
            AtomicInteger calls = new AtomicInteger();
            PaymentGateway lostFirstResponse = new PaymentGateway() {
                @Override
                public ChargeResponse charge(ChargeRequest request) {
                    ChargeResponse response = sandbox.charge(request); // processor handles it...
                    if (calls.getAndIncrement() == 0) {
                        throw new GatewayTimeoutException("read timed out"); // ...but we never hear back
                    }
                    return response;
                }

                @Override
                public void refund(String transactionId, Money amount, String key) {
                    sandbox.refund(transactionId, amount, key);
                }
            };
            PaymentService payments = new PaymentService(lostFirstResponse, RetryPolicy.defaults(), Sleeper.none(), new IdGenerator());

            PaymentOutcome outcome = payments.charge("ord-1", "tok_visa", Money.of("20.00"));

            assertTrue(outcome.approved());
            assertEquals(1, sandbox.capturesFor("ord-1").size());
            assertEquals(sandbox.capturesFor("ord-1").get(0).transactionId(), outcome.transactionId());
        }

        @Test
        void differentOrdersGetDifferentKeys() {
            List<String> keys = new ArrayList<>();
            PaymentGateway recording = new PaymentGateway() {
                public ChargeResponse charge(ChargeRequest r) { keys.add(r.idempotencyKey()); return ChargeResponse.approved("t" + keys.size()); }
                public void refund(String t, Money a, String k) { }
            };
            PaymentService payments = new PaymentService(recording, RetryPolicy.defaults(), Sleeper.none(), new IdGenerator());
            payments.charge("ord-1", "tok", Money.of("1.00"));
            payments.charge("ord-2", "tok", Money.of("1.00"));
            assertEquals(2, new HashSet<>(keys).size());
        }
    }

    // ------------------------------------------------------------------ SUP-103 oversell
    @Nested
    class T3_Oversell {
        @Test
        void concurrentReservationsNeverOversell() throws Exception {
            for (int round = 0; round < 50; round++) {
                InventoryService inventory = new InventoryService(10);
                List<Integer> sink = new ArrayList<>();
                inventory.onLowStock((sku, remaining) -> { synchronized (sink) { sink.add(remaining); } });
                inventory.receive("SNKR", 40);
                AtomicInteger successes = new AtomicInteger();
                runConcurrently(16, () -> {
                    for (int i = 0; i < 10; i++) {
                        if (inventory.reserve("SNKR", 1)) {
                            successes.incrementAndGet();
                        }
                    }
                    return null;
                });
                assertEquals(40, successes.get(), "round " + round);
                assertEquals(new StockSnapshot("SNKR", 40, 0, 40, 0), inventory.snapshot("SNKR"));
            }
        }

        @Test
        void concurrentReleaseAndCommitKeepLedgerBalanced() throws Exception {
            InventoryService inventory = new InventoryService(0);
            inventory.receive("MUG", 10_000);
            runConcurrently(16, () -> {
                for (int i = 0; i < 300; i++) {
                    if (inventory.reserve("MUG", 1)) {
                        if (i % 2 == 0) inventory.commit("MUG", 1); else inventory.release("MUG", 1);
                    }
                }
                return null;
            });
            StockSnapshot s = inventory.snapshot("MUG");
            assertEquals(0, s.reserved(), s.toString());
            assertEquals(s.received(), s.accountedFor(), s.toString());
        }
    }

    // ------------------------------------------------------------------ SUP-104 leaked reservations
    @Nested
    class T4_Leaks {
        @Test
        void partialReservationRolledBackWhenLaterItemOutOfStock() {
            ShipFastApp app = TestStore.create();
            assertThrows(OutOfStockException.class,
                    () -> app.orderService().placeOrder(OrderRequest.of("c-or", new Item("MUG", 2), new Item("LAMP", 21))));
            assertEquals(new StockSnapshot("MUG", 100, 100, 0, 0), app.inventory().snapshot("MUG"));
        }

        @Test
        void stockReleasedWhenProcessorUnavailable() {
            MutableClock clock = new MutableClock(TestStore.NOW);
            SandboxPaymentGateway down = new SandboxPaymentGateway(0L, clock).withConnectTimeoutRate(1.0);
            ShipFastApp app = TestStore.create(down, clock);
            assertThrows(PaymentUnavailableException.class,
                    () -> app.orderService().placeOrder(OrderRequest.of("c-or", new Item("MUG", 2))));
            assertEquals(new StockSnapshot("MUG", 100, 100, 0, 0), app.inventory().snapshot("MUG"));
        }
    }

    // ------------------------------------------------------------------ SUP-105 report boundary
    @Nested
    class T5_Report {
        final MutableClock clock = new MutableClock(TestStore.NOW);
        final ShipFastApp app = TestStore.create(clock);

        void placeAt(LocalDateTime t) {
            clock.set(t.atZone(STORE).toInstant());
            app.orderService().placeOrder(OrderRequest.of("c-or", new Item("MUG", 1)));
        }

        @Test
        void lastSecondOfTheDayIsIncluded() {
            LocalDate day = LocalDate.of(2026, 9, 14);
            placeAt(day.atTime(23, 59, 59, 412_000_000));
            assertEquals(1, app.dailySales().forDate(day).orderCount());
            assertEquals(0, app.dailySales().forDate(day.plusDays(1)).orderCount());
        }

        @Test
        void midnightBelongsToNextDayOnly() {
            LocalDate day = LocalDate.of(2026, 9, 14);
            placeAt(day.plusDays(1).atStartOfDay());
            assertEquals(0, app.dailySales().forDate(day).orderCount());
            assertEquals(1, app.dailySales().forDate(day.plusDays(1)).orderCount());
        }

        @Test
        void fallBackDayHas25Hours() {
            LocalDate day = LocalDate.of(2026, 11, 1);
            placeAt(day.atTime(23, 30));
            assertEquals(1, app.dailySales().forDate(day).orderCount());
        }

        @Test
        void springForwardDayHas23Hours() {
            LocalDate day = LocalDate.of(2026, 3, 8);
            placeAt(day.plusDays(1).atTime(0, 30));
            assertEquals(0, app.dailySales().forDate(day).orderCount());
            assertEquals(1, app.dailySales().forDate(day.plusDays(1)).orderCount());
        }
    }

    // ------------------------------------------------------------------ SUP-106 price drift
    @Nested
    class T6_PriceDrift {
        @Test
        void orderTotalUnaffectedByLaterPriceChange() {
            ShipFastApp app = TestStore.create();
            Order order = app.orderService().placeOrder(OrderRequest.of("c-or", new Item("LAMP", 1)));
            Money charged = order.getTotal();
            app.catalog().updatePrice("LAMP", Money.of("55.00"));
            assertEquals(0, charged.compareTo(order.getTotal()));
            assertEquals(0, Money.of("40.00").compareTo(order.getLines().get(0).unitPrice()));
        }

        @Test
        void fullRefundAfterPriceIncreaseSucceeds() {
            ShipFastApp app = TestStore.create();
            Order order = app.orderService().placeOrder(OrderRequest.of("c-or", new Item("LAMP", 2)));
            app.catalog().updatePrice("LAMP", Money.of("55.00"));
            String amount = app.refunds().refundableBalance(order.getId()).toBigDecimal().toPlainString();
            assertEquals(200, app.support().refund(order.getId(), amount, "return").status());
        }
    }

    // ------------------------------------------------------------------ SUP-107 missing emails
    @Nested
    class T7_Events {
        @Test
        void bulkOrderWithoutCouponIsConfirmed() {
            ShipFastApp app = TestStore.create();
            var response = app.checkout().placeOrder(OrderRequest.of("c-or", new Item("TEA", 10)));
            assertEquals(201, response.status());
            assertEquals(1, app.email().forOrder(response.orderId()).size());
            assertEquals(1, app.audit().entriesFor(response.orderId()).size());
            assertTrue(app.loyalty().balance("c-or") > 0);
        }

        @Test
        void failingHandlerDoesNotStopOthers() {
            EventBus bus = new EventBus();
            List<String> seen = new ArrayList<>();
            bus.subscribe(OrderShippedEvent.class, e -> { throw new IllegalStateException("boom"); });
            bus.subscribe(OrderShippedEvent.class, e -> seen.add(e.orderId()));
            bus.publish(new OrderShippedEvent("ord-1", "a@b.c", "A", "1Z", Instant.EPOCH));
            assertEquals(List.of("ord-1"), seen);
        }
    }

    // ------------------------------------------------------------------ SUP-108 refund status
    @Nested
    class T8_Money {
        @Test
        void fullRefundTypedWithoutTrailingZerosMarksRefunded() {
            ShipFastApp app = TestStore.create();
            Order order = app.orderService().placeOrder(OrderRequest.of("c-or", new Item("LAMP", 2))); // $80.00
            app.support().refund(order.getId(), "80", "return");
            assertEquals(OrderStatus.REFUNDED, order.getStatus());
        }

        @Test
        void equalAmountsAreEqualRegardlessOfScale() {
            assertEquals(Money.of("25.5"), Money.of("25.50"));
            assertEquals(Money.of("25.5").hashCode(), Money.of("25.50").hashCode());
            Set<Money> set = new HashSet<>(List.of(Money.of("0"), Money.of("0.00"), Money.ZERO));
            assertEquals(1, set.size());
        }
    }

    // ------------------------------------------------------------------ helpers
    static void runConcurrently(int threads, java.util.concurrent.Callable<?> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> { start.await(); return task.call(); }));
        }
        start.countDown();
        for (var f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
    }
}
