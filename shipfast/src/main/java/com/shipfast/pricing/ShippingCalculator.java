package com.shipfast.pricing;

import com.shipfast.common.Money;
import com.shipfast.customer.Address;
import com.shipfast.order.OrderLine;
import java.util.List;
import java.util.Set;

/**
 * Flat-rate-plus-weight shipping. Orders over the free-shipping threshold ship free, except to
 * remote regions which always pay the carrier surcharge.
 */
public class ShippingCalculator {

    static final Money FREE_SHIPPING_THRESHOLD = Money.of("50.00");
    static final Money BASE_RATE = Money.of("4.99");
    static final Money PER_EXTRA_HALF_KILO = Money.of("0.75");
    static final Money REMOTE_SURCHARGE = Money.of("12.00");
    static final Set<String> REMOTE_REGIONS = Set.of("AK", "HI", "PR");

    public Money quote(List<OrderLine> lines, Address shipTo, Money merchandiseTotal) {
        boolean remote = REMOTE_REGIONS.contains(shipTo.region());
        if (!remote && !merchandiseTotal.lessThan(FREE_SHIPPING_THRESHOLD)) {
            return Money.ZERO;
        }
        int grams = lines.stream()
                .mapToInt(line -> line.product().getWeightGrams() * line.quantity())
                .sum();
        int halfKilos = Math.max(1, (grams + 499) / 500);
        Money cost = BASE_RATE.plus(PER_EXTRA_HALF_KILO.times(halfKilos - 1));
        if (remote) {
            cost = cost.plus(REMOTE_SURCHARGE);
        }
        return cost;
    }
}
