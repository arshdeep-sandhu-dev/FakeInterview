package com.shipfast.api;

import com.shipfast.common.NotFoundException;
import com.shipfast.common.ValidationException;
import com.shipfast.order.Order;
import com.shipfast.order.OrderRequest;
import com.shipfast.order.OrderService;
import com.shipfast.order.OrderStatus;
import com.shipfast.order.OutOfStockException;
import com.shipfast.payment.PaymentUnavailableException;
import com.shipfast.pricing.CouponRejectedException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Storefront checkout endpoint: {@code POST /checkout}. */
public class CheckoutController {

    private final OrderService orders;
    private final List<String> serverErrors = new CopyOnWriteArrayList<>();

    public CheckoutController(OrderService orders) {
        this.orders = orders;
    }

    public ApiResponse placeOrder(OrderRequest request) {
        try {
            Order order = orders.placeOrder(request);
            if (order.getStatus() == OrderStatus.PAYMENT_DECLINED) {
                return ApiResponse.of(402, "Your card was declined (" + order.getDeclineReason() + ")", order.getId());
            }
            return ApiResponse.of(201, "Order " + order.getId() + " confirmed. Total " + order.getTotal(), order.getId());
        } catch (ValidationException | CouponRejectedException | NotFoundException e) {
            return ApiResponse.of(400, e.getMessage());
        } catch (OutOfStockException e) {
            return ApiResponse.of(409, e.getMessage());
        } catch (PaymentUnavailableException e) {
            return ApiResponse.of(503, "Our payment provider is having trouble. Please try again in a moment.");
        } catch (RuntimeException e) {
            serverErrors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
            return ApiResponse.of(500, "Something went wrong. Please try again.");
        }
    }

    /** Unexpected errors since startup, as they would appear in the error tracker. */
    public List<String> serverErrors() {
        return List.copyOf(serverErrors);
    }
}
