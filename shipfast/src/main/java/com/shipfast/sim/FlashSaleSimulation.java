package com.shipfast.sim;

import com.shipfast.ShipFastApp;
import com.shipfast.api.ApiResponse;
import com.shipfast.common.Money;
import com.shipfast.customer.Customer;
import com.shipfast.order.Order;
import com.shipfast.order.OrderRequest;
import com.shipfast.order.OrderStatus;
import com.shipfast.payment.SandboxPaymentGateway;
import com.shipfast.pricing.Coupon;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Replays a realistic traffic spike against a fully wired {@link ShipFastApp} and then reconciles
 * the result against the payment processor, the warehouse ledger and the finance reports.
 *
 * <p>Scenario, "Black Friday Last Call" (store time, America/Los_Angeles):
 * <ul>
 *   <li>Black Friday sale prices (25% off selected items) end at midnight on 2026-11-27, as does
 *       the {@code LASTCALL} coupon ($10 off $50+, first 100 uses). Traffic ramps up towards
 *       midnight.</li>
 *   <li>23:55 - the limited "Midnight" runner drops: 40 pairs, far more buyers.</li>
 *   <li>23:58:25 - the card processor browns out for ~10 seconds.</li>
 *   <li>00:00 - sale prices revert. Regular traffic continues until 00:15.</li>
 *   <li>09:00 - support processes refund requests from the night before.</li>
 * </ul>
 *
 * <p>Usage: {@code FlashSaleSimulation [--seed N] [--shoppers N] [--threads N]}
 */
public final class FlashSaleSimulation {

    static final LocalDate SALE_DAY = LocalDate.of(2026, 11, 27);
    static final ZoneId ZONE = ShipFastApp.DEFAULT_STORE_ZONE;
    static final String SALE_COUPON = "LASTCALL";
    static final int SALE_COUPON_LIMIT = 100;
    static final String WELCOME_COUPON = "WELCOME10";

    record Options(long seed, int shoppers, int threads) {

        static Options parse(String[] args) {
            long seed = System.nanoTime() % 1_000_000;
            int shoppers = 2000;
            int threads = 8;
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--seed" -> seed = Long.parseLong(args[++i]);
                    case "--shoppers" -> shoppers = Integer.parseInt(args[++i]);
                    case "--threads" -> threads = Integer.parseInt(args[++i]);
                    default -> throw new IllegalArgumentException("Unknown option " + args[i]
                            + ". Usage: [--seed N] [--shoppers N] [--threads N]");
                }
            }
            return new Options(Math.abs(seed), shoppers, threads);
        }
    }

    record Shopper(Instant arrival, OrderRequest request) {
    }

    record CheckoutAttempt(Shopper shopper, ApiResponse response) {
    }

    record RefundAttempt(String orderId, String amount, boolean fullRefund, ApiResponse response) {
    }

    private final Options options;
    private final Random random;
    private final Instant midnight;
    private final Instant opening;
    private final Instant closing;
    private final SimulatedClock clock;
    private final SandboxPaymentGateway gateway;
    private final ShipFastApp app;
    private List<Customer> customers;
    private List<String> regularSkus;

    FlashSaleSimulation(Options options) {
        this.options = options;
        this.random = new Random(options.seed());
        this.midnight = SALE_DAY.plusDays(1).atStartOfDay(ZONE).toInstant();
        this.opening = midnight.minus(Duration.ofMinutes(15));
        this.closing = midnight.plus(Duration.ofMinutes(15));
        this.clock = new SimulatedClock(opening.minus(Duration.ofHours(1)), ZONE);
        this.gateway = new SandboxPaymentGateway(options.seed(), clock)
                .withDeclineRate(0.03)
                .withConnectTimeoutRate(0.005)
                .withReadTimeoutRate(0.01)
                .withMaxLatency(Duration.ofMillis(2))
                .withOutage(midnight.minusSeconds(95), midnight.minusSeconds(85));
        this.app = ShipFastApp.builder()
                .clock(clock)
                .gateway(gateway)
                .sleeper(FlashSaleSimulation::compressedSleep)
                .build();
    }

    public static void main(String[] args) throws Exception {
        FlashSaleSimulation simulation = new FlashSaleSimulation(Options.parse(args));
        simulation.run(System.out);
    }

    void run(PrintStream out) throws InterruptedException {
        setUpStore();
        List<Shopper> lateNight = beforeMidnight();
        List<Shopper> earlyMorning = afterMidnight();

        List<CheckoutAttempt> attempts = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(options.threads());
        try {
            attempts.addAll(runShoppers(pool, lateNight));
            clock.set(midnight);
            endSale();
            attempts.addAll(runShoppers(pool, earlyMorning));
        } finally {
            pool.shutdown();
        }

        clock.set(midnight.plus(Duration.ofHours(9)));
        List<RefundAttempt> refunds = processRefunds();

        List<Reconciliation.Check> checks = Reconciliation.run(
                app, gateway, attempts, refunds, List.of(SALE_DAY, SALE_DAY.plusDays(1)), SALE_COUPON, SALE_COUPON_LIMIT);
        print(out, attempts, refunds, checks);
    }

    // ---------------------------------------------------------------- setup

    private void setUpStore() {
        DemoData.loadCatalog(app);
        customers = DemoData.loadCustomers(app, random, 300);
        regularSkus = DemoData.PRODUCTS.stream()
                .map(DemoData.SeedProduct::sku)
                .filter(sku -> !sku.equals(DemoData.LIMITED_SKU))
                .toList();

        app.coupons().register(Coupon.amountOff(SALE_COUPON, "10", Money.of("50.00"), midnight, SALE_COUPON_LIMIT));
        app.coupons().register(Coupon.percentOff(WELCOME_COUPON, "10", midnight.plus(Duration.ofDays(30))));

        for (DemoData.SeedProduct seed : DemoData.PRODUCTS) {
            if (seed.onSale()) {
                app.catalog().updatePrice(seed.sku(), Money.of(seed.price()).percent(BigDecimal.valueOf(75)));
            }
        }
    }

    private void endSale() {
        for (DemoData.SeedProduct seed : DemoData.PRODUCTS) {
            if (seed.onSale()) {
                app.catalog().updatePrice(seed.sku(), Money.of(seed.price()));
            }
        }
    }

    // ---------------------------------------------------------------- traffic

    private List<Shopper> beforeMidnight() {
        int total = (int) Math.round(options.shoppers() * 0.6);
        int dropBuyers = Math.max(1, options.shoppers() * 6 / 100);
        long window = Duration.between(opening, midnight).toMillis();
        Instant drop = midnight.minus(Duration.ofMinutes(5));

        List<Shopper> shoppers = new ArrayList<>();
        for (int i = 0; i < total - dropBuyers; i++) {
            double u = random.nextDouble();
            long millisBeforeMidnight = 1 + (long) (window * u * u); // busier closer to midnight
            shoppers.add(new Shopper(midnight.minusMillis(millisBeforeMidnight), cart(true, false)));
        }
        for (int i = 0; i < dropBuyers; i++) {
            shoppers.add(new Shopper(drop.plusMillis(random.nextInt(3000)), cart(true, true)));
        }
        shoppers.sort(Comparator.comparing(Shopper::arrival));
        return shoppers;
    }

    private List<Shopper> afterMidnight() {
        int total = options.shoppers() - (int) Math.round(options.shoppers() * 0.6);
        long window = Duration.between(midnight, closing).toMillis();
        List<Shopper> shoppers = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            shoppers.add(new Shopper(midnight.plusMillis((long) (random.nextDouble() * window)), cart(false, false)));
        }
        shoppers.sort(Comparator.comparing(Shopper::arrival));
        return shoppers;
    }

    private OrderRequest cart(boolean saleActive, boolean wantsLimitedDrop) {
        Customer customer = customers.get(random.nextInt(customers.size()));
        List<OrderRequest.Item> items = new ArrayList<>();
        if (customer.business() && !wantsLimitedDrop) {
            String bulkSku = DemoData.BULK_SKUS.get(random.nextInt(DemoData.BULK_SKUS.size()));
            items.add(new OrderRequest.Item(bulkSku, 10 + random.nextInt(21)));
            if (random.nextDouble() < 0.4) {
                items.add(new OrderRequest.Item(randomRegularSku(), 1 + random.nextInt(2)));
            }
        } else {
            int distinct = 1 + random.nextInt(3);
            Set<String> skus = new LinkedHashSet<>();
            while (skus.size() < distinct) {
                skus.add(randomRegularSku());
            }
            for (String sku : skus) {
                items.add(new OrderRequest.Item(sku, random.nextDouble() < 0.15 ? 2 : 1));
            }
            if (wantsLimitedDrop) {
                items.add(random.nextInt(items.size() + 1), new OrderRequest.Item(DemoData.LIMITED_SKU, 1));
            }
        }

        String coupon = null;
        double roll = random.nextDouble();
        if (saleActive) {
            if (roll < 0.40 && !customer.business()) {
                coupon = SALE_COUPON;
            } else if (roll < 0.45) {
                coupon = WELCOME_COUPON.toLowerCase();
            }
        } else if (roll < 0.04) {
            coupon = SALE_COUPON;
        } else if (roll < 0.10) {
            coupon = WELCOME_COUPON;
        }
        return new OrderRequest(customer.id(), items, coupon);
    }

    private String randomRegularSku() {
        return regularSkus.get(random.nextInt(regularSkus.size()));
    }

    private List<CheckoutAttempt> runShoppers(ExecutorService pool, List<Shopper> shoppers) throws InterruptedException {
        List<Callable<CheckoutAttempt>> tasks = new ArrayList<>();
        for (Shopper shopper : shoppers) {
            tasks.add(() -> new CheckoutAttempt(
                    shopper, clock.runAt(shopper.arrival(), () -> app.checkout().placeOrder(shopper.request()))));
        }
        List<CheckoutAttempt> results = new ArrayList<>();
        for (Future<CheckoutAttempt> future : pool.invokeAll(tasks)) {
            try {
                results.add(future.get());
            } catch (ExecutionException e) {
                throw new IllegalStateException("Simulated shopper crashed outside the checkout controller", e.getCause());
            }
        }
        return results;
    }

    // ---------------------------------------------------------------- support

    /** Support agents work through the overnight refund queue using the admin UI. */
    private List<RefundAttempt> processRefunds() {
        List<RefundAttempt> attempts = new ArrayList<>();
        for (Order order : app.orders().findAll()) {
            if (order.getStatus() != OrderStatus.PAID) {
                continue;
            }
            double roll = random.nextDouble();
            if (roll < 0.05) {
                // "Refund in full": the admin UI pre-fills the refundable balance.
                String amount = asTypedInAdminUi(app.refunds().refundableBalance(order.getId()));
                attempts.add(new RefundAttempt(order.getId(), amount, true,
                        app.support().refund(order.getId(), amount, "changed mind")));
            } else if (roll < 0.08) {
                String amount = asTypedInAdminUi(order.getTotal().dividedBy(2));
                attempts.add(new RefundAttempt(order.getId(), amount, false,
                        app.support().refund(order.getId(), amount, "item arrived damaged")));
            }
        }
        return attempts;
    }

    /** The admin UI is JavaScript: amounts are posted as JS numbers, e.g. 25.50 becomes "25.5". */
    private static String asTypedInAdminUi(Money amount) {
        return amount.toBigDecimal().stripTrailingZeros().toPlainString();
    }

    // ---------------------------------------------------------------- output

    private void print(PrintStream out, List<CheckoutAttempt> attempts, List<RefundAttempt> refunds,
                       List<Reconciliation.Check> checks) {
        String rule = "=".repeat(86);
        out.println(rule);
        out.printf(" ShipFast load simulation: \"Black Friday Last Call\"   (seed %d)%n", options.seed());
        out.printf(" %,d shoppers on %d threads, %s 23:45 -> %s 00:15 %s%n",
                options.shoppers(), options.threads(), SALE_DAY, SALE_DAY.plusDays(1), ZONE);
        out.println(rule);

        out.println();
        out.println("Checkout responses");
        Map<Integer, Long> byStatus = attempts.stream()
                .collect(Collectors.groupingBy(a -> a.response().status(), TreeMap::new, Collectors.counting()));
        byStatus.forEach((status, count) -> out.printf("  HTTP %d  %,6d%n", status, count));

        List<String> errors = app.checkout().serverErrors();
        if (!errors.isEmpty()) {
            out.println();
            out.println("Error tracker (unhandled exceptions at checkout)");
            errors.stream()
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                    .entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(3)
                    .forEach(e -> out.printf("  %4dx %s%n", e.getValue(), e.getKey()));
        }

        out.println();
        long refundOk = refunds.stream().filter(r -> r.response().isSuccess()).count();
        out.printf("Support refunds: %d requested, %d succeeded%n", refunds.size(), refundOk);

        out.println();
        out.println("Finance: daily sales reports");
        out.print(indent(app.dailySales().forDate(SALE_DAY).format()));
        out.print(indent(app.dailySales().forDate(SALE_DAY.plusDays(1)).format()));

        out.println();
        out.println("Reconciliation");
        for (Reconciliation.Check check : checks) {
            out.printf("  [%s] %s%n", check.ok() ? " OK " : "FAIL", check.name());
            if (!check.ok()) {
                out.printf("         -> %s%n", check.detail());
            }
        }
        long failed = checks.stream().filter(check -> !check.ok()).count();
        out.println();
        out.printf("%d of %d checks failed.%n", failed, checks.size());
        out.println("(Thread scheduling means numbers vary a little between runs, even with the same seed.)");
    }

    private static String indent(String text) {
        return text.lines().map(line -> "  " + line).collect(Collectors.joining("\n", "", "\n"));
    }

    /** Backoff sleeps are compressed 20x so a simulated night takes seconds, not minutes. */
    private static void compressedSleep(Duration duration) {
        try {
            Thread.sleep(Math.max(1, duration.toMillis() / 20));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
