package com.shipfast.api;

import com.shipfast.common.Money;
import com.shipfast.common.NotFoundException;
import com.shipfast.common.ValidationException;
import com.shipfast.order.Order;
import com.shipfast.order.OrderRepository;
import com.shipfast.order.Refund;
import com.shipfast.payment.RefundRejectedException;
import com.shipfast.refund.RefundService;

/**
 * Endpoints used by the internal support admin UI. Amounts arrive as the raw strings typed or
 * pre-filled in the UI's form fields.
 */
public class SupportController {

    private final OrderRepository orders;
    private final RefundService refunds;

    public SupportController(OrderRepository orders, RefundService refunds) {
        this.orders = orders;
        this.refunds = refunds;
    }

    /** {@code GET /support/orders/{id}} */
    public ApiResponse orderDetails(String orderId) {
        try {
            Order order = orders.require(orderId);
            String body = order.getId()
                    + " status=" + order.getStatus()
                    + " total=" + order.getTotal()
                    + " refunded=" + order.getRefunded()
                    + " refundable=" + refunds.refundableBalance(orderId);
            return ApiResponse.of(200, body, orderId);
        } catch (NotFoundException e) {
            return ApiResponse.of(404, e.getMessage());
        }
    }

    /** {@code POST /support/orders/{id}/refunds} */
    public ApiResponse refund(String orderId, String amount, String reason) {
        try {
            Refund refund = refunds.refund(orderId, Money.of(amount), reason);
            Order order = orders.require(orderId);
            return ApiResponse.of(200,
                    "Refunded " + refund.amount() + " (" + refund.id() + "). Order is now " + order.getStatus(), orderId);
        } catch (NotFoundException e) {
            return ApiResponse.of(404, e.getMessage());
        } catch (ValidationException | IllegalArgumentException e) {
            return ApiResponse.of(400, e.getMessage());
        } catch (RefundRejectedException e) {
            return ApiResponse.of(502, "Payment processor rejected the refund: " + e.getMessage());
        }
    }
}
