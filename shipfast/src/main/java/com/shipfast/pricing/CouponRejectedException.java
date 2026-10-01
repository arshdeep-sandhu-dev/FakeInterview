package com.shipfast.pricing;

public class CouponRejectedException extends RuntimeException {

    public enum Reason {
        UNKNOWN_CODE,
        EXPIRED,
        EXHAUSTED,
        BELOW_MINIMUM_SPEND
    }

    private final String code;
    private final Reason reason;

    public CouponRejectedException(String code, Reason reason) {
        super("Coupon '" + code + "' rejected: " + reason);
        this.code = code;
        this.reason = reason;
    }

    public String getCode() {
        return code;
    }

    public Reason getReason() {
        return reason;
    }
}
