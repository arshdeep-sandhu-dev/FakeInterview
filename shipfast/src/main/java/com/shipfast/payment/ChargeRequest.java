package com.shipfast.payment;

import com.shipfast.common.Money;

/**
 * @param idempotencyKey the processor treats requests with the same key as the same charge
 * @param reference      our identifier for what is being paid for (the order id)
 */
public record ChargeRequest(String idempotencyKey, String paymentToken, Money amount, String reference) {
}
