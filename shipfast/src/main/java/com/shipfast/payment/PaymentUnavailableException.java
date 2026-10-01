package com.shipfast.payment;

/** Payment could not be completed because the processor is unreachable. Maps to HTTP 503. */
public class PaymentUnavailableException extends RuntimeException {

    public PaymentUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
