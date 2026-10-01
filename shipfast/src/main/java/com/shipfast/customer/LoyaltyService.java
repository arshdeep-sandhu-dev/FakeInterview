package com.shipfast.customer;

import com.shipfast.common.Money;
import com.shipfast.common.ValidationException;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks loyalty point balances. Points are earned per whole dollar of merchandise spend. */
public class LoyaltyService {

    private final Map<String, Integer> balances = new ConcurrentHashMap<>();

    /** Awards points for a purchase and returns the number of points awarded. */
    public int award(String customerId, Money merchandiseSpend, LoyaltyTier tier) {
        if (!merchandiseSpend.isPositive()) {
            return 0;
        }
        int wholeDollars = merchandiseSpend.toBigDecimal().setScale(0, RoundingMode.DOWN).intValueExact();
        int points = wholeDollars * tier.pointsPerDollar();
        if (points > 0) {
            balances.merge(customerId, points, Integer::sum);
        }
        return points;
    }

    public void redeem(String customerId, int points) {
        if (points <= 0) {
            throw new ValidationException("Points to redeem must be positive");
        }
        balances.compute(customerId, (id, balance) -> {
            int current = balance == null ? 0 : balance;
            if (current < points) {
                throw new ValidationException("Insufficient points: have " + current + ", need " + points);
            }
            return current - points;
        });
    }

    public int balance(String customerId) {
        return balances.getOrDefault(customerId, 0);
    }
}
