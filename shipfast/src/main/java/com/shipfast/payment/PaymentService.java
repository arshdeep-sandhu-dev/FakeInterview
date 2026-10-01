package com.shipfast.payment;

import com.shipfast.common.IdGenerator;
import com.shipfast.common.Money;

/**
 * Charges customers through the {@link PaymentGateway}, retrying transient timeouts according to
 * the {@link RetryPolicy}.
 */
public class PaymentService {

    private final PaymentGateway gateway;
    private final RetryPolicy retryPolicy;
    private final Sleeper sleeper;
    private final IdGenerator ids;

    public PaymentService(PaymentGateway gateway, RetryPolicy retryPolicy, Sleeper sleeper, IdGenerator ids) {
        this.gateway = gateway;
        this.retryPolicy = retryPolicy;
        this.sleeper = sleeper;
        this.ids = ids;
    }

    /**
     * Charges {@code amount} to the customer's card for the given order.
     *
     * @return approved or declined outcome
     * @throws PaymentUnavailableException if the gateway kept timing out
     */
    public PaymentOutcome charge(String orderId, String paymentToken, Money amount) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Charge amount must be positive, was " + amount);
        }
        int attempt = 1;
        while (true) {
            ChargeRequest request = new ChargeRequest(ids.next("chg"), paymentToken, amount, orderId);
            try {
                ChargeResponse response = gateway.charge(request);
                return response.approved()
                        ? PaymentOutcome.approved(response.transactionId(), amount, attempt)
                        : PaymentOutcome.declined(response.declineCode(), attempt);
            } catch (GatewayTimeoutException e) {
                if (attempt >= retryPolicy.maxAttempts()) {
                    throw new PaymentUnavailableException(
                            "Payment gateway timed out " + attempt + " times for " + orderId, e);
                }
                attempt++;
                sleeper.sleep(retryPolicy.backoffBefore(attempt));
            }
        }
    }
}
