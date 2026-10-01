package com.shipfast.pricing;

import com.shipfast.common.Money;
import java.util.List;

/**
 * The result of pricing a cart.
 *
 * @param discount   total of all promotions (bulk pricing + coupon)
 * @param couponCode the coupon applied, or {@code null}
 * @param promotions names of every promotion that contributed to {@code discount}
 */
public record PriceBreakdown(
        Money subtotal,
        Money discount,
        Money tax,
        Money shipping,
        Money total,
        String couponCode,
        List<String> promotions) {

    public Money merchandiseTotal() {
        return subtotal.minus(discount);
    }
}
