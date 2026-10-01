package com.shipfast.sim;

import com.shipfast.ShipFastApp;
import com.shipfast.common.Money;
import com.shipfast.inventory.StockSnapshot;
import com.shipfast.order.Order;
import com.shipfast.order.OrderStatus;
import com.shipfast.payment.SandboxPaymentGateway;
import com.shipfast.payment.SandboxPaymentGateway.Capture;
import com.shipfast.pricing.Coupon;
import com.shipfast.reporting.SalesSummary;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cross-checks the store's own records against independent sources of truth: the processor's
 * captures, the warehouse ledger, the outbox and the customer-facing responses.
 */
final class Reconciliation {

    record Check(String name, boolean ok, String detail) {
    }

    private Reconciliation() {
    }

    static List<Check> run(ShipFastApp app,
                           SandboxPaymentGateway gateway,
                           List<FlashSaleSimulation.CheckoutAttempt> checkouts,
                           List<FlashSaleSimulation.RefundAttempt> refunds,
                           List<LocalDate> days,
                           String limitedCoupon,
                           int couponLimit) {
        List<Check> checks = new ArrayList<>();
        List<Order> orders = app.orders().findAll();
        Map<String, Order> ordersById = orders.stream().collect(Collectors.toMap(Order::getId, Function.identity()));
        List<Order> paid = orders.stream().filter(o -> o.getStatus().countsAsRevenue()).toList();
        List<Capture> captures = gateway.captures();
        Map<String, List<Capture>> capturesByOrder = captures.stream().collect(Collectors.groupingBy(Capture::reference));
        Map<String, Capture> captureByTransaction = captures.stream()
                .collect(Collectors.toMap(Capture::transactionId, Function.identity()));

        // --- payments
        int multiCharged = 0;
        Money overCollected = Money.ZERO;
        for (List<Capture> forOrder : capturesByOrder.values()) {
            if (forOrder.size() > 1) {
                multiCharged++;
                for (Capture extra : forOrder.subList(1, forOrder.size())) {
                    overCollected = overCollected.plus(extra.amount());
                }
            }
        }
        checks.add(new Check("Each order was charged at most once", multiCharged == 0,
                multiCharged + " orders charged more than once; " + overCollected + " over-collected"));

        long orphaned = captures.stream()
                .filter(c -> !ordersById.containsKey(c.reference())
                        || !ordersById.get(c.reference()).getStatus().countsAsRevenue())
                .count();
        checks.add(new Check("Every charge belongs to a paid order", orphaned == 0,
                orphaned + " charges have no paid order behind them"));

        // --- inventory
        List<String> unbalanced = new ArrayList<>();
        int stuckUnits = 0;
        Set<String> stuckSkus = new TreeSet<>();
        for (String sku : new TreeSet<>(app.inventory().skus())) {
            StockSnapshot s = app.inventory().snapshot(sku);
            if (s.accountedFor() != s.received()) {
                unbalanced.add(sku + " (received " + s.received() + ", accounted for " + s.accountedFor() + ")");
            }
            if (s.reserved() != 0) {
                stuckUnits += s.reserved();
                stuckSkus.add(sku);
            }
        }
        checks.add(new Check("Stock ledger balances (received = available + reserved + sold)", unbalanced.isEmpty(),
                String.join(", ", unbalanced)));
        checks.add(new Check("No stock left in 'reserved' once checkout is idle", stuckUnits == 0,
                stuckUnits + " units stuck across " + stuckSkus.size() + " SKUs " + stuckSkus));

        // --- promotions
        Coupon coupon = app.coupons().find(limitedCoupon).orElseThrow();
        checks.add(new Check(limitedCoupon + " used no more than its limit of " + couponLimit,
                coupon.getTimesUsed() <= couponLimit,
                coupon.getTimesUsed() + " redemptions"));

        // --- customer experience
        long withoutEmail = paid.stream().filter(o -> app.email().forOrder(o.getId()).isEmpty()).count();
        checks.add(new Check("Every paid order got a confirmation email", withoutEmail == 0,
                withoutEmail + " paid orders have no confirmation email"));

        Set<String> confirmedToCustomer = checkouts.stream()
                .filter(a -> a.response().status() == 201)
                .map(a -> a.response().orderId())
                .collect(Collectors.toSet());
        long chargedButErrored = paid.stream().filter(o -> !confirmedToCustomer.contains(o.getId())).count();
        checks.add(new Check("No customer was charged but shown an error", chargedButErrored == 0,
                chargedButErrored + " customers were charged but saw an error page"));

        // --- finance
        List<String> reportGaps = new ArrayList<>();
        for (LocalDate day : days) {
            SalesSummary summary = app.dailySales().forDate(day);
            long actual = paid.stream()
                    .filter(o -> o.getPlacedAt().atZone(FlashSaleSimulation.ZONE).toLocalDate().equals(day))
                    .count();
            if (summary.orderCount() != actual) {
                reportGaps.add(day + ": report shows " + summary.orderCount() + " orders, actual " + actual);
            }
        }
        checks.add(new Check("Daily sales report includes every paid order", reportGaps.isEmpty(),
                String.join("; ", reportGaps)));

        List<String> drifted = new ArrayList<>();
        for (Order order : paid) {
            Capture capture = captureByTransaction.get(order.getPaymentTransactionId());
            if (capture != null && capture.amount().compareTo(order.getTotal()) != 0) {
                drifted.add(order.getId() + " charged " + capture.amount() + ", now shows " + order.getTotal());
            }
        }
        checks.add(new Check("Order totals still match what the customer was charged", drifted.isEmpty(),
                drifted.size() + " orders drifted, e.g. " + drifted.stream().limit(2).collect(Collectors.joining("; "))));

        List<String> wrongStatus = new ArrayList<>();
        for (Order order : paid) {
            Capture capture = captureByTransaction.get(order.getPaymentTransactionId());
            if (capture != null
                    && order.getRefunded().compareTo(capture.amount()) == 0
                    && order.getStatus() != OrderStatus.REFUNDED) {
                wrongStatus.add(order.getId() + " (" + order.getStatus() + ")");
            }
        }
        checks.add(new Check("Fully refunded orders are marked REFUNDED", wrongStatus.isEmpty(),
                wrongStatus.size() + " orders, e.g. " + wrongStatus.stream().limit(3).collect(Collectors.joining(", "))));

        List<String> failedRefunds = refunds.stream()
                .filter(r -> !r.response().isSuccess())
                .map(r -> r.orderId() + " HTTP " + r.response().status() + ": " + r.response().body())
                .toList();
        checks.add(new Check("Support refund requests succeeded", failedRefunds.isEmpty(),
                failedRefunds.size() + " of " + refunds.size() + " failed, e.g. " + failedRefunds.stream().findFirst().orElse("")));

        return checks;
    }
}
