package com.shipfast.pricing;

import com.shipfast.common.Money;
import com.shipfast.customer.Address;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Destination-based sales tax. Rates are simplified state-level rates; local surtaxes are not
 * modelled.
 */
public class TaxCalculator {

    static final BigDecimal DEFAULT_RATE = new BigDecimal("5.00");

    private static final Map<String, BigDecimal> STATE_RATES = Map.ofEntries(
            Map.entry("CA", new BigDecimal("7.25")),
            Map.entry("TX", new BigDecimal("6.25")),
            Map.entry("NY", new BigDecimal("4.00")),
            Map.entry("WA", new BigDecimal("6.50")),
            Map.entry("IL", new BigDecimal("6.25")),
            Map.entry("FL", new BigDecimal("6.00")),
            Map.entry("MA", new BigDecimal("6.25")),
            Map.entry("CO", new BigDecimal("2.90")),
            Map.entry("OR", BigDecimal.ZERO),
            Map.entry("MT", BigDecimal.ZERO),
            Map.entry("NH", BigDecimal.ZERO),
            Map.entry("DE", BigDecimal.ZERO),
            Map.entry("AK", BigDecimal.ZERO));

    public BigDecimal rateFor(Address shipTo) {
        if (!"US".equals(shipTo.country())) {
            return BigDecimal.ZERO; // exports are zero-rated
        }
        return STATE_RATES.getOrDefault(shipTo.region(), DEFAULT_RATE);
    }

    public Money taxOn(Money taxableAmount, Address shipTo) {
        return taxableAmount.percent(rateFor(shipTo));
    }
}
