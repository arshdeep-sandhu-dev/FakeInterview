package com.shipfast.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shipfast.catalog.Category;
import com.shipfast.catalog.Product;
import com.shipfast.common.Money;
import com.shipfast.common.ValidationException;
import com.shipfast.customer.Address;
import com.shipfast.order.OrderLine;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingEngineTest {

    private static final Address OREGON = Address.us("1 Pine St", "Portland", "OR", "97201");
    private static final Address CALIFORNIA = Address.us("2 Palm Ave", "San Diego", "CA", "92101");
    private static final Address ALASKA = Address.us("3 Spruce Rd", "Juneau", "AK", "99801");
    private static final Instant FAR_FUTURE = Instant.parse("2099-01-01T00:00:00Z");

    private final Product mug = new Product("MUG", "Mug", Category.HOME, Money.of("12.50"), 400);
    private final Product lamp = new Product("LAMP", "Lamp", Category.HOME, Money.of("40.00"), 1200);
    private final Product tea = new Product("TEA", "Tea", Category.GROCERY, Money.of("6.00"), 250);

    private final PricingEngine pricing = new PricingEngine(new TaxCalculator(), new ShippingCalculator());

    @Test
    void subtotalIsSumOfLines() {
        PriceBreakdown price = pricing.quote(List.of(new OrderLine(mug, 2), new OrderLine(tea, 1)), OREGON, null);
        assertEquals(Money.of("31.00"), price.subtotal());
    }

    @Test
    void taxUsesDestinationStateRate() {
        PriceBreakdown price = pricing.quote(List.of(new OrderLine(mug, 2)), CALIFORNIA, null);
        // 7.25% of 25.00 = 1.8125
        assertEquals(Money.of("1.81"), price.tax());
    }

    @Test
    void oregonHasNoSalesTax() {
        PriceBreakdown price = pricing.quote(List.of(new OrderLine(lamp, 1)), OREGON, null);
        assertTrue(price.tax().isZero());
    }

    @Test
    void shippingIsFreeAtThreshold() {
        PriceBreakdown price = pricing.quote(List.of(new OrderLine(lamp, 2)), OREGON, null);
        assertTrue(price.shipping().isZero());
    }

    @Test
    void shippingBelowThresholdIsChargedByWeight() {
        // 1 mug = 400g -> one half-kilo -> base rate
        assertEquals(Money.of("4.99"), pricing.quote(List.of(new OrderLine(mug, 1)), OREGON, null).shipping());
        // 3 mugs = 1200g -> three half-kilos -> base + 2 x 0.75
        assertEquals(Money.of("6.49"), pricing.quote(List.of(new OrderLine(mug, 3)), OREGON, null).shipping());
    }

    @Test
    void remoteRegionsAlwaysPaySurcharge() {
        PriceBreakdown price = pricing.quote(List.of(new OrderLine(lamp, 2)), ALASKA, null);
        // 2400g -> 5 half-kilos -> 4.99 + 4 x 0.75 + 12.00
        assertEquals(Money.of("19.99"), price.shipping());
    }

    @Test
    void totalIsMerchandisePlusTaxPlusShipping() {
        PriceBreakdown price = pricing.quote(List.of(new OrderLine(mug, 2)), CALIFORNIA, null);
        // 25.00 + 1.81 tax + 5.74 shipping (800g)
        assertEquals(Money.of("32.55"), price.total());
    }

    @Test
    void bulkDiscountAppliesFromTenUnitsOfOneSku() {
        PriceBreakdown bulk = pricing.quote(List.of(new OrderLine(tea, 10)), OREGON, null);
        assertEquals(Money.of("3.00"), bulk.discount());
        assertEquals(List.of(PricingEngine.BULK_PROMOTION), bulk.promotions());

        PriceBreakdown notBulk = pricing.quote(List.of(new OrderLine(tea, 9)), OREGON, null);
        assertTrue(notBulk.discount().isZero());
        assertTrue(notBulk.promotions().isEmpty());
    }

    @Test
    void couponAppliesAfterBulkDiscount() {
        Coupon tenPercent = Coupon.percentOff("TEN", "10", FAR_FUTURE);
        PriceBreakdown price = pricing.quote(List.of(new OrderLine(tea, 10)), OREGON, tenPercent);
        // bulk: 5% of 60.00 = 3.00; coupon: 10% of 57.00 = 5.70
        assertEquals(Money.of("8.70"), price.discount());
        assertEquals("TEN", price.couponCode());
        assertEquals(List.of(PricingEngine.BULK_PROMOTION, "TEN"), price.promotions());
    }

    @Test
    void amountOffCouponNeverExceedsSubtotal() {
        Coupon tenOff = Coupon.amountOff("TENOFF", "10", Money.ZERO, FAR_FUTURE, 0);
        PriceBreakdown price = pricing.quote(List.of(new OrderLine(tea, 1)), OREGON, tenOff);
        assertEquals(Money.of("6.00"), price.discount());
    }

    @Test
    void emptyCartCannotBePriced() {
        assertThrows(ValidationException.class, () -> pricing.quote(List.of(), OREGON, null));
    }
}
