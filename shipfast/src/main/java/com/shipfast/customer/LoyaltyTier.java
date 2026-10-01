package com.shipfast.customer;

public enum LoyaltyTier {
    STANDARD(1),
    SILVER(2),
    GOLD(3);

    private final int pointsPerDollar;

    LoyaltyTier(int pointsPerDollar) {
        this.pointsPerDollar = pointsPerDollar;
    }

    public int pointsPerDollar() {
        return pointsPerDollar;
    }
}
