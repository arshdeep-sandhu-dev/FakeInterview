package com.shipfast.api;

/**
 * Transport-agnostic response returned by controllers. The HTTP layer maps {@code status} straight
 * onto the response code.
 *
 * @param orderId the order the response refers to, when there is one
 */
public record ApiResponse(int status, String body, String orderId) {

    public static ApiResponse of(int status, String body) {
        return new ApiResponse(status, body, null);
    }

    public static ApiResponse of(int status, String body, String orderId) {
        return new ApiResponse(status, body, orderId);
    }

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}
