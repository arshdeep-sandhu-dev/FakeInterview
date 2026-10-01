package com.shipfast.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.shipfast.MutableClock;
import com.shipfast.common.Money;
import com.shipfast.pricing.CouponRejectedException.Reason;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CouponServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-10T17:00:00Z");
    private static final Money SUBTOTAL = Money.of("80.00");

    private final MutableClock clock = new MutableClock(NOW);
    private final CouponService coupons = new CouponService(clock);

    @Test
    void acceptsValidCoupon() {
        Coupon coupon = Coupon.percentOff("SPRING", "15", NOW.plus(Duration.ofDays(7)));
        coupons.register(coupon);
        assertSame(coupon, coupons.validate("SPRING", SUBTOTAL));
    }

    @Test
    void codesAreCaseInsensitiveAndTrimmed() {
        Coupon coupon = Coupon.percentOff("SPRING", "15", NOW.plus(Duration.ofDays(7)));
        coupons.register(coupon);
        assertSame(coupon, coupons.validate("  spring ", SUBTOTAL));
    }

    @Test
    void rejectsUnknownCode() {
        CouponRejectedException e = assertThrows(CouponRejectedException.class,
                () -> coupons.validate("NOPE", SUBTOTAL));
        assertEquals(Reason.UNKNOWN_CODE, e.getReason());
    }

    @Test
    void rejectsExpiredCoupon() {
        coupons.register(Coupon.percentOff("SPRING", "15", NOW.plus(Duration.ofDays(7))));
        clock.advance(Duration.ofDays(8));
        CouponRejectedException e = assertThrows(CouponRejectedException.class,
                () -> coupons.validate("SPRING", SUBTOTAL));
        assertEquals(Reason.EXPIRED, e.getReason());
    }

    @Test
    void couponIsNotValidAtItsExpiryInstant() {
        coupons.register(Coupon.percentOff("SPRING", "15", NOW));
        assertThrows(CouponRejectedException.class, () -> coupons.validate("SPRING", SUBTOTAL));
    }

    @Test
    void rejectsCartsBelowMinimumSpend() {
        coupons.register(Coupon.amountOff("TENOFF", "10", Money.of("50.00"), NOW.plus(Duration.ofDays(1)), 0));
        CouponRejectedException e = assertThrows(CouponRejectedException.class,
                () -> coupons.validate("TENOFF", Money.of("49.99")));
        assertEquals(Reason.BELOW_MINIMUM_SPEND, e.getReason());
    }

    /**
     * SUP-101, the exact numbers from the ticket: QA registered a limit-3 coupon and found it
     * could be used 4 times, never 5. That's what a strict {@code >} boundary check does - at
     * exactly {@code timesUsed == maxUses} it says "not exhausted yet" and allows one more. This
     * is a single-threaded, sequential reproduction with no concurrency involved at all, to isolate
     * that boundary bug from the separate race-condition bug covered by {@link CouponConcurrencyTest}.
     */
    @Test
    void qaReproduction_limitOfThreeAllowsExactlyThreeUsesNotFour() {
        Coupon coupon = Coupon.amountOff("FIRST3", "5", Money.ZERO, NOW.plus(Duration.ofDays(1)), 3);
        coupons.register(coupon);

        for (int use = 1; use <= 3; use++) {
            Coupon validated = coupons.validate("FIRST3", SUBTOTAL);
            coupons.redeem(validated);
        }
        assertEquals(3, coupon.getTimesUsed());

        CouponRejectedException e = assertThrows(CouponRejectedException.class,
                () -> coupons.validate("FIRST3", SUBTOTAL));
        assertEquals(Reason.EXHAUSTED, e.getReason());
    }

    @Test
    void limitedCouponIsRejectedOnceUsedUp() {
        Coupon coupon = Coupon.amountOff("FIRST3", "5", Money.ZERO, NOW.plus(Duration.ofDays(1)), 3);
        coupons.register(coupon);
        for (int i = 0; i < 5; i++) {
            coupons.redeem(coupon);
        }
        CouponRejectedException e = assertThrows(CouponRejectedException.class,
                () -> coupons.validate("FIRST3", SUBTOTAL));
        assertEquals(Reason.EXHAUSTED, e.getReason());
    }

    @Test
    void unlimitedCouponNeverRunsOut() {
        Coupon coupon = Coupon.percentOff("FOREVER", "5", NOW.plus(Duration.ofDays(365)));
        coupons.register(coupon);
        for (int i = 0; i < 1_000; i++) {
            coupons.redeem(coupon);
        }
        assertSame(coupon, coupons.validate("FOREVER", SUBTOTAL));
    }
}
