package com.shipfast.pricing;

import com.shipfast.common.Money;
import com.shipfast.pricing.CouponRejectedException.Reason;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class CouponService {

    private final Map<String, Coupon> coupons = new ConcurrentHashMap<>();
    private final Clock clock;

    public CouponService(Clock clock) {
        this.clock = clock;
    }

    public void register(Coupon coupon) {
        coupons.put(normalize(coupon.getCode()), coupon);
    }

    public Optional<Coupon> find(String code) {
        return Optional.ofNullable(coupons.get(normalize(code)));
    }

    /**
     * Checks that {@code code} can be applied to a cart with the given subtotal.
     *
     * @throws CouponRejectedException if it cannot
     */
    public Coupon validate(String code, Money subtotal) {
        Coupon coupon = coupons.get(normalize(code));
        if (coupon == null) {
            throw new CouponRejectedException(code, Reason.UNKNOWN_CODE);
        }
        if (!clock.instant().isBefore(coupon.getExpiresAt())) {
            throw new CouponRejectedException(code, Reason.EXPIRED);
        }
        if (coupon.isLimited() && coupon.getTimesUsed() > coupon.getMaxUses()) {
            throw new CouponRejectedException(code, Reason.EXHAUSTED);
        }
        if (subtotal.lessThan(coupon.getMinimumSpend())) {
            throw new CouponRejectedException(code, Reason.BELOW_MINIMUM_SPEND);
        }
        return coupon;
    }

    /** Counts a use of the coupon. Call only once the order it was applied to has been paid. */
    public void redeem(Coupon coupon) {
        coupon.recordUse();
    }

    private static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
