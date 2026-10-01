package com.shipfast;

import com.shipfast.catalog.Category;
import com.shipfast.catalog.Product;
import com.shipfast.common.Money;
import com.shipfast.customer.Address;
import com.shipfast.customer.Customer;
import com.shipfast.customer.LoyaltyTier;
import com.shipfast.payment.PaymentGateway;
import com.shipfast.payment.SandboxPaymentGateway;
import com.shipfast.payment.Sleeper;
import java.time.Instant;

/**
 * A small, fully wired store for tests.
 *
 * <pre>
 * Products                         Customers
 *   MUG   $12.50  400g  stock 100    c-or      Oregon (no sales tax), card OK
 *   LAMP  $40.00 1200g  stock  20    c-ca      California (7.25%), GOLD tier
 *   TEA    $6.00  250g  stock 500    c-decline Oregon, card always declined
 * </pre>
 */
public final class TestStore {

    /** Tuesday 2026-03-10 10:00 in the store's time zone (America/Los_Angeles, PDT). */
    public static final Instant NOW = Instant.parse("2026-03-10T17:00:00Z");

    private TestStore() {
    }

    public static ShipFastApp create() {
        return create(new SandboxPaymentGateway(), new MutableClock(NOW));
    }

    public static ShipFastApp create(MutableClock clock) {
        return create(new SandboxPaymentGateway(0L, clock), clock);
    }

    public static ShipFastApp create(PaymentGateway gateway, MutableClock clock) {
        ShipFastApp app = ShipFastApp.builder()
                .clock(clock)
                .gateway(gateway)
                .sleeper(Sleeper.none())
                .build();

        app.catalog().add(new Product("MUG", "Stoneware Mug", Category.HOME, Money.of("12.50"), 400));
        app.catalog().add(new Product("LAMP", "Desk Lamp", Category.HOME, Money.of("40.00"), 1200));
        app.catalog().add(new Product("TEA", "Green Tea", Category.GROCERY, Money.of("6.00"), 250));
        app.inventory().receive("MUG", 100);
        app.inventory().receive("LAMP", 20);
        app.inventory().receive("TEA", 500);

        app.customers().save(new Customer("c-or", "Olive Reyes", "olive@example.com",
                Address.us("1 Pine St", "Portland", "OR", "97201"), "tok_visa_1", LoyaltyTier.STANDARD, false));
        app.customers().save(new Customer("c-ca", "Carl Adams", "carl@example.com",
                Address.us("2 Palm Ave", "San Diego", "CA", "92101"), "tok_visa_2", LoyaltyTier.GOLD, false));
        app.customers().save(new Customer("c-decline", "Dana Cole", "dana@example.com",
                Address.us("3 Fir St", "Salem", "OR", "97301"), "tok_decline_1", LoyaltyTier.STANDARD, false));
        return app;
    }
}
