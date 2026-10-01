package com.shipfast.common;

/** Thrown when a referenced entity (order, product, customer) does not exist. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
