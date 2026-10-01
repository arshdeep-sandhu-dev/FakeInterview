package com.shipfast.order;

public enum OrderStatus {
    PAYMENT_PENDING,
    PAID,
    PAYMENT_DECLINED,
    SHIPPED,
    PARTIALLY_REFUNDED,
    REFUNDED;

    /** Orders that took the customer's money at some point and belong in sales figures. */
    public boolean countsAsRevenue() {
        return this == PAID || this == SHIPPED || this == PARTIALLY_REFUNDED || this == REFUNDED;
    }

    public boolean isRefundable() {
        return this == PAID || this == SHIPPED || this == PARTIALLY_REFUNDED;
    }
}
