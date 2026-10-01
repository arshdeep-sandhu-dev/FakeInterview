package com.shipfast.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void addsAmounts() {
        assertEquals(Money.of("15.00"), Money.of("10.25").plus(Money.of("4.75")));
    }

    @Test
    void subtractsAmounts() {
        assertEquals(Money.of("5.50"), Money.of("10.25").minus(Money.of("4.75")));
    }

    @Test
    void multipliesByQuantity() {
        assertEquals(Money.of("9.30"), Money.of("3.10").times(3));
    }

    @Test
    void percentRoundsToTheCentUsingBankersRounding() {
        // 7.25% of 10.00 = 0.725 -> 0.72 (round half to even)
        assertEquals(Money.of("0.72"), Money.of("10.00").percent(new BigDecimal("7.25")));
        // 7.25% of 30.00 = 2.175 -> 2.18
        assertEquals(Money.of("2.18"), Money.of("30.00").percent(new BigDecimal("7.25")));
    }

    @Test
    void dividesIntoEqualShares() {
        assertEquals(Money.of("3.33"), Money.of("10.00").dividedBy(3));
    }

    @Test
    void comparesAmounts() {
        Money small = Money.of("1.00");
        Money large = Money.of("2.00");
        assertTrue(large.greaterThan(small));
        assertTrue(small.lessThan(large));
        assertFalse(small.greaterThan(small));
        assertEquals(small, small.min(large));
        assertEquals(large, small.max(large));
    }

    @Test
    void knowsItsSign() {
        assertTrue(Money.ZERO.isZero());
        assertTrue(Money.of("0.01").isPositive());
        assertTrue(Money.of("-0.01").isNegative());
    }

    @Test
    void formatsWithTwoDecimals() {
        assertEquals("$5.00", Money.of("5").toString());
        assertEquals("$19.99", Money.of("19.99").toString());
    }

    @Test
    void convertsToCents() {
        assertEquals(1999, Money.of("19.99").toCents());
        assertEquals(Money.of("19.99"), Money.ofCents(1999));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> Money.of("twelve"));
        assertThrows(IllegalArgumentException.class, () -> Money.of("10.00").dividedBy(0));
    }
}
