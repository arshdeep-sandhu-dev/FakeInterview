package com.shipfast;

import com.shipfast.api.CheckoutController;
import com.shipfast.api.SupportController;
import com.shipfast.audit.AuditLog;
import com.shipfast.audit.OrderAuditListener;
import com.shipfast.catalog.ProductCatalog;
import com.shipfast.common.IdGenerator;
import com.shipfast.customer.CustomerRepository;
import com.shipfast.customer.LoyaltyListener;
import com.shipfast.customer.LoyaltyService;
import com.shipfast.events.EventBus;
import com.shipfast.events.OrderPlacedEvent;
import com.shipfast.events.OrderShippedEvent;
import com.shipfast.inventory.InventoryService;
import com.shipfast.notification.ConfirmationEmailListener;
import com.shipfast.notification.InMemoryEmailSender;
import com.shipfast.notification.ShipmentEmailListener;
import com.shipfast.order.OrderRepository;
import com.shipfast.order.OrderService;
import com.shipfast.payment.PaymentGateway;
import com.shipfast.payment.PaymentService;
import com.shipfast.payment.RetryPolicy;
import com.shipfast.payment.SandboxPaymentGateway;
import com.shipfast.payment.Sleeper;
import com.shipfast.pricing.CouponService;
import com.shipfast.pricing.PricingEngine;
import com.shipfast.pricing.ShippingCalculator;
import com.shipfast.pricing.TaxCalculator;
import com.shipfast.refund.RefundService;
import com.shipfast.reporting.DailySalesReport;
import java.time.Clock;
import java.time.ZoneId;

/**
 * Composition root: wires every service together. In production the HTTP layer holds a single
 * instance of this; tests and the simulator build their own with controlled clocks and gateways.
 */
public final class ShipFastApp {

    public static final ZoneId DEFAULT_STORE_ZONE = ZoneId.of("America/Los_Angeles");

    private final Clock clock;
    private final IdGenerator ids;
    private final ProductCatalog catalog;
    private final CustomerRepository customers;
    private final InventoryService inventory;
    private final CouponService coupons;
    private final PricingEngine pricing;
    private final PaymentGateway gateway;
    private final PaymentService payments;
    private final OrderRepository orders;
    private final EventBus events;
    private final AuditLog audit;
    private final LoyaltyService loyalty;
    private final InMemoryEmailSender email;
    private final OrderService orderService;
    private final RefundService refunds;
    private final DailySalesReport dailySales;
    private final CheckoutController checkout;
    private final SupportController support;

    private ShipFastApp(Builder b) {
        this.clock = b.clock;
        this.ids = new IdGenerator();
        this.catalog = new ProductCatalog(clock);
        this.customers = new CustomerRepository();
        this.inventory = new InventoryService(b.lowStockThreshold);
        this.coupons = new CouponService(clock);
        this.pricing = new PricingEngine(new TaxCalculator(), new ShippingCalculator());
        this.gateway = b.gateway;
        this.payments = new PaymentService(gateway, b.retryPolicy, b.sleeper, ids);
        this.orders = new OrderRepository();
        this.events = new EventBus();
        this.audit = new AuditLog(clock);
        this.loyalty = new LoyaltyService();
        this.email = new InMemoryEmailSender();
        this.orderService = new OrderService(
                catalog, customers, inventory, pricing, coupons, payments, orders, events, ids, clock);
        this.refunds = new RefundService(orders, gateway, audit, ids, clock);
        this.dailySales = new DailySalesReport(orders, b.storeZone);
        this.checkout = new CheckoutController(orderService);
        this.support = new SupportController(orders, refunds);

        inventory.onLowStock((sku, remaining) -> audit.record("LOW_STOCK", sku, "remaining=" + remaining));

        // Audit first so there is always a record, even if a later handler misbehaves.
        events.subscribe(OrderPlacedEvent.class, new OrderAuditListener(audit));
        events.subscribe(OrderPlacedEvent.class, new LoyaltyListener(loyalty));
        events.subscribe(OrderPlacedEvent.class, new ConfirmationEmailListener(email));
        events.subscribe(OrderShippedEvent.class, new ShipmentEmailListener(email));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Clock clock() {
        return clock;
    }

    public IdGenerator ids() {
        return ids;
    }

    public ProductCatalog catalog() {
        return catalog;
    }

    public CustomerRepository customers() {
        return customers;
    }

    public InventoryService inventory() {
        return inventory;
    }

    public CouponService coupons() {
        return coupons;
    }

    public PricingEngine pricing() {
        return pricing;
    }

    public PaymentGateway gateway() {
        return gateway;
    }

    public PaymentService payments() {
        return payments;
    }

    public OrderRepository orders() {
        return orders;
    }

    public EventBus events() {
        return events;
    }

    public AuditLog audit() {
        return audit;
    }

    public LoyaltyService loyalty() {
        return loyalty;
    }

    public InMemoryEmailSender email() {
        return email;
    }

    public OrderService orderService() {
        return orderService;
    }

    public RefundService refunds() {
        return refunds;
    }

    public DailySalesReport dailySales() {
        return dailySales;
    }

    public CheckoutController checkout() {
        return checkout;
    }

    public SupportController support() {
        return support;
    }

    public static final class Builder {

        private Clock clock = Clock.systemUTC();
        private PaymentGateway gateway = new SandboxPaymentGateway();
        private RetryPolicy retryPolicy = RetryPolicy.defaults();
        private Sleeper sleeper = Sleeper.system();
        private ZoneId storeZone = DEFAULT_STORE_ZONE;
        private int lowStockThreshold = InventoryService.DEFAULT_LOW_STOCK_THRESHOLD;

        private Builder() {
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder gateway(PaymentGateway gateway) {
            this.gateway = gateway;
            return this;
        }

        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        public Builder sleeper(Sleeper sleeper) {
            this.sleeper = sleeper;
            return this;
        }

        public Builder storeZone(ZoneId storeZone) {
            this.storeZone = storeZone;
            return this;
        }

        public Builder lowStockThreshold(int lowStockThreshold) {
            this.lowStockThreshold = lowStockThreshold;
            return this;
        }

        public ShipFastApp build() {
            return new ShipFastApp(this);
        }
    }
}
