package com.shipfast.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shipfast.MutableClock;
import com.shipfast.ShipFastApp;
import com.shipfast.TestStore;
import com.shipfast.common.Money;
import com.shipfast.order.Order;
import com.shipfast.order.OrderRequest;
import com.shipfast.order.OrderRequest.Item;
import com.shipfast.order.OrderStatus;
import com.shipfast.payment.SandboxPaymentGateway;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * Reproduces SUP-101: many concurrent checkouts must never redeem a limited coupon more times
 * than its {@code maxUses}, even though the redemption is only counted after the (slow) payment
 * call completes.
 *
 * <p>These are inherently probabilistic: a race that doesn't fire on a given run isn't proof the
 * race is gone. {@link #neverExceedsLimitAcrossManyIndependentRuns()} exists specifically to raise
 * confidence beyond a single run by repeating a smaller race many times over.
 */
class CouponConcurrencyTest {

    @Test
    void concurrentCheckoutsNeverExceedTheCouponLimit() throws Exception {
        Result result = race(5, 40, 16, "MUG");
        assertTrue(result.paidWithCoupon <= 5,
                "coupon limit was 5 but " + result.paidWithCoupon + " orders redeemed it");
        assertEquals(result.paidWithCoupon, result.timesUsed);
    }

    /**
     * The tightest possible boundary: a single-use coupon (the common case for a referral or
     * "welcome" code) under heavy contention. This is also the case most sensitive to an off-by-one
     * in the limit check, since there's no room at all between "0 used" and "exhausted".
     */
    @Test
    void singleUseCouponUnderContentionNeverGoesToTwoCustomers() throws Exception {
        Result result = race(1, 50, 20, "TEA");
        assertTrue(result.paidWithCoupon <= 1,
                "coupon allowed only 1 use but " + result.paidWithCoupon + " orders redeemed it");
        assertEquals(result.paidWithCoupon, result.timesUsed);
    }

    /**
     * One race can pass by luck even when the underlying bug is still there, since it depends on
     * the OS scheduler lining threads up inside a small window. Repeating a smaller race 20 times,
     * each with its own fresh coupon and thread pool, means 20 independent chances for a surviving
     * race to show itself instead of one.
     */
    @RepeatedTest(20)
    void neverExceedsLimitAcrossManyIndependentRuns() throws Exception {
        Result result = race(3, 20, 8, "MUG");
        assertTrue(result.paidWithCoupon <= 3,
                "coupon limit was 3 but " + result.paidWithCoupon + " orders redeemed it");
        assertEquals(result.paidWithCoupon, result.timesUsed);
    }

    private record Result(long paidWithCoupon, int timesUsed) {
    }

    /**
     * Fires {@code shoppers} concurrent checkouts, all for the same one-use coupon on a
     * {@code sku}, released from a single starting gate so they contend as hard as possible.
     */
    private Result race(int limit, int shoppers, int threads, String sku) throws Exception {
        String code = "RACE" + limit + "-" + shoppers;
        MutableClock clock = new MutableClock(TestStore.NOW);
        // Real latency on every charge widens the validate() -> claim()/redeem() window, which is
        // exactly the gap the bug (and the fix) is about.
        SandboxPaymentGateway gateway = new SandboxPaymentGateway(0L, clock).withMaxLatency(Duration.ofMillis(5));
        ShipFastApp app = TestStore.create(gateway, clock);
        Coupon coupon = Coupon.amountOff(code, "1", Money.ZERO, TestStore.NOW.plus(Duration.ofDays(1)), limit);
        app.coupons().register(coupon);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        // A CouponRejectedException(EXHAUSTED) is an expected outcome once the limit is reached,
        // not a test failure - it's the correct behaviour we're trying to force to happen reliably
        // instead of racily. Only an unexpected exception should fail the test.
        List<Callable<Order>> checkouts = java.util.stream.IntStream.range(0, shoppers)
                .<Callable<Order>>mapToObj(i -> () -> {
                    start.await();
                    try {
                        return app.orderService().placeOrder(
                                OrderRequest.of("c-or", new Item(sku, 1)).withCoupon(code));
                    } catch (CouponRejectedException e) {
                        return null;
                    }
                })
                .toList();

        List<Future<Order>> futures = checkouts.stream().map(pool::submit).toList();
        start.countDown();
        long paidWithCoupon = 0;
        for (Future<Order> future : futures) {
            Order order = future.get(30, TimeUnit.SECONDS);
            if (order != null && order.getStatus() == OrderStatus.PAID && code.equals(order.getCouponCode())) {
                paidWithCoupon++;
            }
        }
        pool.shutdown();
        return new Result(paidWithCoupon, coupon.getTimesUsed());
    }
}
