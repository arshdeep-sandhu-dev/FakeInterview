package com.shipfast.payment;

public record ChargeResponse(boolean approved, String transactionId, String declineCode) {

    public static ChargeResponse approved(String transactionId) {
        return new ChargeResponse(true, transactionId, null);
    }

    public static ChargeResponse declined(String declineCode) {
        return new ChargeResponse(false, null, declineCode);
    }
}
