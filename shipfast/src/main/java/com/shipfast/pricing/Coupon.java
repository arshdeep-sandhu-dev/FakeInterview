package com.shipfast.pricing;

import com.shipfast.common.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class Coupon {

    public enum Type {
        PERCENT_OFF,
        AMOUNT_OFF
    }

    private final String code;
    private final Type type;
    private final BigDecimal value;
    private final Money minimumSpend;
    private final Instant expiresAt;
    private final int maxUses;
    private final AtomicInteger timesUsed = new AtomicInteger();

    /**
     * @param value     percentage (e.g. {@code 15} for 15% off) or dollar amount, depending on type
     * @param expiresAt the coupon is valid strictly before this instant
     * @param maxUses   maximum number of redemptions across all customers; {@code 0} = unlimited
     */
    public Coupon(String code, Type type, BigDecimal value, Money minimumSpend, Instant expiresAt, int maxUses) {
        this.code = Objects.requireNonNull(code, "code");
        this.type = Objects.requireNonNull(type, "type");
        this.value = Objects.requireNonNull(value, "value");
        this.minimumSpend = minimumSpend == null ? Money.ZERO : minimumSpend;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Coupon value must be positive");
        }
        if (type == Type.PERCENT_OFF && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Percent-off coupon cannot exceed 100%");
        }
        if (maxUses < 0) {
            throw new IllegalArgumentException("maxUses cannot be negative");
        }
        this.maxUses = maxUses;
    }

    public static Coupon percentOff(String code, String percent, Instant expiresAt) {
        return new Coupon(code, Type.PERCENT_OFF, new BigDecimal(percent), Money.ZERO, expiresAt, 0);
    }

    public static Coupon amountOff(String code, String amount, Money minimumSpend, Instant expiresAt, int maxUses) {
        return new Coupon(code, Type.AMOUNT_OFF, new BigDecimal(amount), minimumSpend, expiresAt, maxUses);
    }

    /** The discount this coupon gives on {@code eligibleAmount}; never more than the amount itself. */
    public Money discountFor(Money eligibleAmount) {
        Money discount = type == Type.PERCENT_OFF
                ? eligibleAmount.percent(value)
                : Money.of(value);
        return discount.min(eligibleAmount);
    }

    public String getCode() {
        return code;
    }

    public Type getType() {
        return type;
    }

    public BigDecimal getValue() {
        return value;
    }

    public Money getMinimumSpend() {
        return minimumSpend;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getMaxUses() {
        return maxUses;
    }

    public boolean isLimited() {
        return maxUses > 0;
    }

    public int getTimesUsed() {
        return timesUsed.get();
    }

    void recordUse() {
        timesUsed.incrementAndGet();
    }

    /**
     * Atomically checks the limit and takes one use in a single step, so concurrent callers can't
     * all pass the check before any of them increments. Returns {@code false} (no side effect) if
     * no uses are left.
     */
    boolean tryClaim() {
        while (true) {
            int used = timesUsed.get();
            if (isLimited() && used >= maxUses) {
                return false;
            }
            if (timesUsed.compareAndSet(used, used + 1)) {
                return true;
            }
            // another thread claimed or released a use between get() and compareAndSet(); retry
        }
    }

    /** Gives back a use that was claimed for an order that did not end up paid. */
    void unclaim() {
        timesUsed.decrementAndGet();
    }
}
