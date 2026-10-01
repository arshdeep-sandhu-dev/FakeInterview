package com.shipfast.payment;

/** The payment processor did not respond within the client timeout. */
public class GatewayTimeoutException extends RuntimeException {

    public GatewayTimeoutException(String message) {
        super(message);
    }
}
