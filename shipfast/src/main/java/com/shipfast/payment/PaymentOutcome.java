package com.shipfast.payment;

import com.shipfast.common.Money;

/**
 * @param attempts how many gateway calls it took to get an answer
 */
public record PaymentOutcome(boolean approved, String transactionId, String declineCode, Money amount, int attempts) {

    public static PaymentOutcome approved(String transactionId, Money amount, int attempts) {
        return new PaymentOutcome(true, transactionId, null, amount, attempts);
    }

    public static PaymentOutcome declined(String declineCode, int attempts) {
        return new PaymentOutcome(false, null, declineCode, Money.ZERO, attempts);
    }
}
