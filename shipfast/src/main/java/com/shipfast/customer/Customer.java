package com.shipfast.customer;

import java.util.Objects;

/**
 * A registered shopper.
 *
 * @param paymentToken tokenised card on file (we never store raw card numbers)
 * @param business     true for B2B accounts, which typically order in bulk
 */
public record Customer(
        String id,
        String name,
        String email,
        Address shippingAddress,
        String paymentToken,
        LoyaltyTier tier,
        boolean business) {

    public Customer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(shippingAddress, "shippingAddress");
        Objects.requireNonNull(paymentToken, "paymentToken");
        tier = tier == null ? LoyaltyTier.STANDARD : tier;
    }
}
