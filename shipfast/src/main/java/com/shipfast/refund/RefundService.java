package com.shipfast.refund;

import com.shipfast.audit.AuditLog;
import com.shipfast.common.IdGenerator;
import com.shipfast.common.Money;
import com.shipfast.common.ValidationException;
import com.shipfast.order.Order;
import com.shipfast.order.OrderRepository;
import com.shipfast.order.Refund;
import com.shipfast.payment.PaymentGateway;
import java.time.Clock;

/** Issues full or partial refunds against paid orders. */
public class RefundService {

    private final OrderRepository orders;
    private final PaymentGateway gateway;
    private final AuditLog audit;
    private final IdGenerator ids;
    private final Clock clock;

    public RefundService(OrderRepository orders, PaymentGateway gateway, AuditLog audit, IdGenerator ids, Clock clock) {
        this.orders = orders;
        this.gateway = gateway;
        this.audit = audit;
        this.ids = ids;
        this.clock = clock;
    }

    public Refund refund(String orderId, Money amount, String reason) {
        Order order = orders.require(orderId);
        synchronized (order) {
            if (!order.getStatus().isRefundable()) {
                throw new ValidationException("Order " + orderId + " cannot be refunded while " + order.getStatus());
            }
            if (!amount.isPositive()) {
                throw new ValidationException("Refund amount must be positive");
            }
            Money remaining = order.getTotal().minus(order.getRefunded());
            if (amount.greaterThan(remaining)) {
                throw new ValidationException("Refund of " + amount + " exceeds refundable balance " + remaining);
            }

            String refundId = ids.next("rf");
            gateway.refund(order.getPaymentTransactionId(), amount, refundId);

            Refund refund = new Refund(refundId, amount, reason, clock.instant());
            order.recordRefund(refund);
            if (order.getRefunded().equals(order.getTotal())) {
                order.markRefunded();
            } else {
                order.markPartiallyRefunded();
            }
            audit.record("REFUND", orderId, refundId + " amount=" + amount + " reason=" + reason);
            return refund;
        }
    }

    public Money refundableBalance(String orderId) {
        Order order = orders.require(orderId);
        return order.getTotal().minus(order.getRefunded());
    }
}
