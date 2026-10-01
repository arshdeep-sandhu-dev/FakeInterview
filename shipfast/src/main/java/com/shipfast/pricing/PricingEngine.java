package com.shipfast.pricing;

import com.shipfast.common.Money;
import com.shipfast.common.ValidationException;
import com.shipfast.customer.Address;
import com.shipfast.order.OrderLine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Prices a cart: subtotal, promotions, tax and shipping.
 *
 * <p>Promotion order matters: automatic bulk pricing is applied first, then the coupon is applied
 * to what remains. Tax and the free-shipping threshold are based on the discounted amount.
 */
public class PricingEngine {

    public static final String BULK_PROMOTION = "BULK5";
    static final int BULK_MIN_QUANTITY = 10;
    static final BigDecimal BULK_PERCENT = new BigDecimal("5");

    private final TaxCalculator taxes;
    private final ShippingCalculator shipping;

    public PricingEngine(TaxCalculator taxes, ShippingCalculator shipping) {
        this.taxes = taxes;
        this.shipping = shipping;
    }

    /**
     * @param coupon an already-validated coupon, or {@code null}
     */
    public PriceBreakdown quote(List<OrderLine> lines, Address shipTo, Coupon coupon) {
        if (lines == null || lines.isEmpty()) {
            throw new ValidationException("Cannot price an empty cart");
        }
        Money subtotal = subtotal(lines);
        List<String> promotions = new ArrayList<>();
        Money discount = Money.ZERO;

        Money bulk = bulkDiscount(lines);
        if (bulk.isPositive()) {
            discount = discount.plus(bulk);
            promotions.add(BULK_PROMOTION);
        }
        if (coupon != null) {
            Money couponDiscount = coupon.discountFor(subtotal.minus(discount));
            discount = discount.plus(couponDiscount);
            promotions.add(coupon.getCode());
        }

        Money merchandise = subtotal.minus(discount);
        Money tax = taxes.taxOn(merchandise, shipTo);
        Money shippingCost = shipping.quote(lines, shipTo, merchandise);
        Money total = merchandise.plus(tax).plus(shippingCost);

        return new PriceBreakdown(
                subtotal,
                discount,
                tax,
                shippingCost,
                total,
                coupon == null ? null : coupon.getCode(),
                List.copyOf(promotions));
    }

    public Money subtotal(List<OrderLine> lines) {
        Money subtotal = Money.ZERO;
        for (OrderLine line : lines) {
            subtotal = subtotal.plus(line.lineTotal());
        }
        return subtotal;
    }

    /** 5% off any line with 10 or more units of the same SKU. */
    Money bulkDiscount(List<OrderLine> lines) {
        Money total = Money.ZERO;
        for (OrderLine line : lines) {
            if (line.quantity() >= BULK_MIN_QUANTITY) {
                total = total.plus(line.lineTotal().percent(BULK_PERCENT));
            }
        }
        return total;
    }
}
