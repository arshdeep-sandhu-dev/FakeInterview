package com.shipfast.notification;

/**
 * @param orderId the order this email relates to, if any
 */
public record Email(String to, String subject, String body, String orderId) {
}
