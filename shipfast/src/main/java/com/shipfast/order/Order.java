package com.shipfast.order;

import com.shipfast.common.Money;
import com.shipfast.pricing.PriceBreakdown;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Order {

    private final String id;
    private final String customerId;
    private final List<OrderLine> lines;
    private final Instant placedAt;
    private final String couponCode;
    private final List<String> promotions;
    private final Money discount;
    private final Money tax;
    private final Money shipping;

    private OrderStatus status = OrderStatus.PAYMENT_PENDING;
    private String paymentTransactionId;
    private String declineReason;
    private String trackingNumber;
    private final List<Refund> refunds = new ArrayList<>();
    private Money refunded = Money.ZERO;

    public Order(String id, String customerId, List<OrderLine> lines, PriceBreakdown price, Instant placedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.lines = List.copyOf(lines);
        this.placedAt = Objects.requireNonNull(placedAt, "placedAt");
        this.couponCode = price.couponCode();
        this.promotions = List.copyOf(price.promotions());
        this.discount = price.discount();
        this.tax = price.tax();
        this.shipping = price.shipping();
    }

    public String getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public List<OrderLine> getLines() {
        return lines;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public List<String> getPromotions() {
        return promotions;
    }

    public Money getSubtotal() {
        Money subtotal = Money.ZERO;
        for (OrderLine line : lines) {
            subtotal = subtotal.plus(line.lineTotal());
        }
        return subtotal;
    }

    public Money getDiscount() {
        return discount;
    }

    public Money getTax() {
        return tax;
    }

    public Money getShipping() {
        return shipping;
    }

    public Money getTotal() {
        return getSubtotal().minus(discount).plus(tax).plus(shipping);
    }

    public int getUnitCount() {
        return lines.stream().mapToInt(OrderLine::quantity).sum();
    }

    public synchronized OrderStatus getStatus() {
        return status;
    }

    public synchronized String getPaymentTransactionId() {
        return paymentTransactionId;
    }

    public synchronized String getDeclineReason() {
        return declineReason;
    }

    public synchronized String getTrackingNumber() {
        return trackingNumber;
    }

    public synchronized Money getRefunded() {
        return refunded;
    }

    public synchronized List<Refund> getRefunds() {
        return List.copyOf(refunds);
    }

    public synchronized void markPaid(String transactionId) {
        requireStatus(OrderStatus.PAYMENT_PENDING);
        this.paymentTransactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.status = OrderStatus.PAID;
    }

    public synchronized void markDeclined(String reason) {
        requireStatus(OrderStatus.PAYMENT_PENDING);
        this.declineReason = reason;
        this.status = OrderStatus.PAYMENT_DECLINED;
    }

    public synchronized void markShipped(String trackingNumber) {
        requireStatus(OrderStatus.PAID);
        this.trackingNumber = trackingNumber;
        this.status = OrderStatus.SHIPPED;
    }

    public synchronized void recordRefund(Refund refund) {
        refunds.add(refund);
        refunded = refunded.plus(refund.amount());
    }

    public synchronized void markRefunded() {
        this.status = OrderStatus.REFUNDED;
    }

    public synchronized void markPartiallyRefunded() {
        this.status = OrderStatus.PARTIALLY_REFUNDED;
    }

    private void requireStatus(OrderStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Order " + id + " is " + status + ", expected " + expected);
        }
    }

    @Override
    public String toString() {
        return "Order[" + id + ", " + getStatus() + ", total " + getTotal() + "]";
    }
}
