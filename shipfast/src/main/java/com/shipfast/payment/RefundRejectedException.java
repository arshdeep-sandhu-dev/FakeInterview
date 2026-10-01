package com.shipfast.payment;

/** The processor refused a refund (unknown transaction, or more than was captured). */
public class RefundRejectedException extends RuntimeException {

    public RefundRejectedException(String message) {
        super(message);
    }
}
