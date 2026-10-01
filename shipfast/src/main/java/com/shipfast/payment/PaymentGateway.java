package com.shipfast.payment;

import com.shipfast.common.Money;

/** Client for the external card processor. */
public interface PaymentGateway {

    /**
     * Authorises and captures a card payment.
     *
     * @throws GatewayTimeoutException if the processor did not respond in time
     */
    ChargeResponse charge(ChargeRequest request);

    /**
     * Refunds part or all of a previous capture.
     *
     * @throws RefundRejectedException if the processor refuses the refund
     */
    void refund(String transactionId, Money amount, String idempotencyKey);
}
