package com.shipfast.common;

/** Thrown when a request is well-formed but violates a business rule. Maps to HTTP 400. */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
