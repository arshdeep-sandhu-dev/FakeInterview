package com.shipfast.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable monetary amount in the store currency (USD).
 *
 * <p>All arithmetic is exact except {@link #percent} and {@link #dividedBy}, which round to the
 * nearest cent using banker's rounding.
 */
public final class Money implements Comparable<Money> {

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        this.amount = Objects.requireNonNull(amount, "amount");
    }

    /** Parses a decimal string such as {@code "19.99"} or {@code "5"}. */
    public static Money of(String amount) {
        Objects.requireNonNull(amount, "amount");
        try {
            return new Money(new BigDecimal(amount.trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not a valid amount: '" + amount + "'", e);
        }
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public static Money ofCents(long cents) {
        return new Money(BigDecimal.valueOf(cents, 2));
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money minus(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money times(int quantity) {
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)));
    }

    /** Returns {@code percent}% of this amount, rounded to the cent. */
    public Money percent(BigDecimal percent) {
        return new Money(amount.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_EVEN));
    }

    /** Splits this amount into {@code parts} equal shares, rounded to the cent. */
    public Money dividedBy(int parts) {
        if (parts <= 0) {
            throw new IllegalArgumentException("parts must be positive");
        }
        return new Money(amount.divide(BigDecimal.valueOf(parts), 2, RoundingMode.HALF_EVEN));
    }

    public Money min(Money other) {
        return compareTo(other) <= 0 ? this : other;
    }

    public Money max(Money other) {
        return compareTo(other) >= 0 ? this : other;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean greaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean lessThan(Money other) {
        return compareTo(other) < 0;
    }

    public long toCents() {
        return amount.setScale(2, RoundingMode.HALF_EVEN).movePointRight(2).longValueExact();
    }

    public BigDecimal toBigDecimal() {
        return amount;
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Money)) {
            return false;
        }
        return amount.equals(((Money) o).amount);
    }

    @Override
    public int hashCode() {
        return amount.hashCode();
    }

    @Override
    public String toString() {
        return "$" + amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }
}
