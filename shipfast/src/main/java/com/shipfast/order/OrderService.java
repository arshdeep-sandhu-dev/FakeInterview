package com.shipfast.order;

import com.shipfast.catalog.Product;
import com.shipfast.catalog.ProductCatalog;
import com.shipfast.common.IdGenerator;
import com.shipfast.common.Money;
import com.shipfast.common.NotFoundException;
import com.shipfast.common.ValidationException;
import com.shipfast.customer.Customer;
import com.shipfast.customer.CustomerRepository;
import com.shipfast.events.EventBus;
import com.shipfast.events.OrderPlacedEvent;
import com.shipfast.events.OrderShippedEvent;
import com.shipfast.inventory.InventoryService;
import com.shipfast.payment.PaymentOutcome;
import com.shipfast.payment.PaymentService;
import com.shipfast.pricing.Coupon;
import com.shipfast.pricing.CouponService;
import com.shipfast.pricing.PriceBreakdown;
import com.shipfast.pricing.PricingEngine;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checkout orchestration.
 *
 * <p>Placing an order: validate cart -> price -> reserve stock -> charge card -> on success commit
 * stock, redeem coupon, publish {@link OrderPlacedEvent}; on decline release stock.
 */
public class OrderService {

    static final int MAX_DISTINCT_ITEMS = 50;

    private final ProductCatalog catalog;
    private final CustomerRepository customers;
    private final InventoryService inventory;
    private final PricingEngine pricing;
    private final CouponService coupons;
    private final PaymentService payments;
    private final OrderRepository orders;
    private final EventBus events;
    private final IdGenerator ids;
    private final Clock clock;

    public OrderService(
            ProductCatalog catalog,
            CustomerRepository customers,
            InventoryService inventory,
            PricingEngine pricing,
            CouponService coupons,
            PaymentService payments,
            OrderRepository orders,
            EventBus events,
            IdGenerator ids,
            Clock clock) {
        this.catalog = catalog;
        this.customers = customers;
        this.inventory = inventory;
        this.pricing = pricing;
        this.coupons = coupons;
        this.payments = payments;
        this.orders = orders;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
    }

    public Order placeOrder(OrderRequest request) {
        Customer customer = customers.find(request.customerId())
                .orElseThrow(() -> new NotFoundException("Unknown customer " + request.customerId()));
        List<OrderLine> lines = toLines(request);

        Coupon coupon = null;
        if (request.hasCoupon()) {
            coupon = coupons.validate(request.couponCode(), pricing.subtotal(lines));
            // Claim the use now, atomically, rather than after payment: validate() + a later
            // increment left a window across the whole card charge for other checkouts to also
            // pass validate() before any of them incremented the counter.
            coupons.claim(coupon);
        }

        PriceBreakdown price;
        Order order;
        PaymentOutcome payment;
        try {
            price = pricing.quote(lines, customer.shippingAddress(), coupon);
            reserveStock(lines);
            order = new Order(ids.next("ord"), customer.id(), lines, price, clock.instant());
            payment = payments.charge(order.getId(), customer.paymentToken(), price.total());
        } catch (RuntimeException e) {
            // Nothing after claim() settled the order one way or the other (out of stock, the
            // gateway being unreachable, or anything else) - give the slot back, since claim()
            // is the only thing that took it.
            if (coupon != null) {
                coupons.unclaim(coupon);
            }
            throw e;
        }

        if (!payment.approved()) {
            releaseStock(lines);
            if (coupon != null) {
                coupons.unclaim(coupon);
            }
            order.markDeclined(payment.declineCode());
            orders.save(order);
            return order;
        }

        commitStock(lines);
        order.markPaid(payment.transactionId());
        orders.save(order);
        events.publish(placedEvent(order, customer, price));
        return order;
    }

    public Order ship(String orderId, String trackingNumber) {
        Order order = orders.require(orderId);
        order.markShipped(trackingNumber);
        Customer customer = customers.find(order.getCustomerId())
                .orElseThrow(() -> new NotFoundException("Unknown customer " + order.getCustomerId()));
        events.publish(new OrderShippedEvent(
                order.getId(), customer.email(), customer.name(), trackingNumber, clock.instant()));
        return order;
    }

    private List<OrderLine> toLines(OrderRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new ValidationException("Order must contain at least one item");
        }
        // The storefront can send the same SKU twice (e.g. added from two pages); merge them.
        Map<String, Integer> quantities = new LinkedHashMap<>();
        for (OrderRequest.Item item : request.items()) {
            if (item.quantity() <= 0) {
                throw new ValidationException("Quantity must be positive for " + item.sku());
            }
            quantities.merge(item.sku(), item.quantity(), Integer::sum);
        }
        if (quantities.size() > MAX_DISTINCT_ITEMS) {
            throw new ValidationException("Too many distinct items in one order");
        }
        List<OrderLine> lines = new ArrayList<>();
        quantities.forEach((sku, quantity) -> {
            Product product = catalog.find(sku)
                    .filter(Product::isActive)
                    .orElseThrow(() -> new ValidationException("Unknown or discontinued product " + sku));
            lines.add(new OrderLine(product, quantity));
        });
        return lines;
    }

    private void reserveStock(List<OrderLine> lines) {
        for (OrderLine line : lines) {
            if (!inventory.reserve(line.sku(), line.quantity())) {
                throw new OutOfStockException(line.sku(), line.quantity());
            }
        }
    }

    private void releaseStock(List<OrderLine> lines) {
        for (OrderLine line : lines) {
            inventory.release(line.sku(), line.quantity());
        }
    }

    private void commitStock(List<OrderLine> lines) {
        for (OrderLine line : lines) {
            inventory.commit(line.sku(), line.quantity());
        }
    }

    private OrderPlacedEvent placedEvent(Order order, Customer customer, PriceBreakdown price) {
        List<OrderPlacedEvent.LineItem> items = order.getLines().stream()
                .map(line -> new OrderPlacedEvent.LineItem(
                        line.sku(), line.product().getName(), line.quantity(), line.unitPrice(), line.lineTotal()))
                .toList();
        return new OrderPlacedEvent(
                order.getId(),
                customer.id(),
                customer.name(),
                customer.email(),
                customer.tier(),
                items,
                price.subtotal(),
                price.discount(),
                price.tax(),
                price.shipping(),
                price.total(),
                price.couponCode(),
                price.promotions(),
                order.getPlacedAt());
    }

    /** Current price of a cart without placing it, for the cart page. */
    public PriceBreakdown preview(OrderRequest request) {
        Customer customer = customers.find(request.customerId())
                .orElseThrow(() -> new NotFoundException("Unknown customer " + request.customerId()));
        List<OrderLine> lines = toLines(request);
        Coupon coupon = request.hasCoupon() ? coupons.validate(request.couponCode(), pricing.subtotal(lines)) : null;
        return pricing.quote(lines, customer.shippingAddress(), coupon);
    }

    /** Total of everything a customer has paid for, net of refunds. */
    public Money lifetimeSpend(String customerId) {
        Money total = Money.ZERO;
        for (Order order : orders.findByCustomer(customerId)) {
            if (order.getStatus().countsAsRevenue()) {
                total = total.plus(order.getTotal()).minus(order.getRefunded());
            }
        }
        return total;
    }
}
