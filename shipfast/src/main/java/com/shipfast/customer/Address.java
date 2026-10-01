package com.shipfast.customer;

import java.util.Objects;

/**
 * A shipping address. {@code region} is the two-letter US state / territory code.
 */
public record Address(String line1, String city, String region, String postalCode, String country) {

    public Address {
        Objects.requireNonNull(line1, "line1");
        Objects.requireNonNull(region, "region");
        country = country == null ? "US" : country;
    }

    public static Address us(String line1, String city, String region, String postalCode) {
        return new Address(line1, city, region, postalCode, "US");
    }
}
