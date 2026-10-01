package com.shipfast.reporting;

import com.shipfast.catalog.Category;
import com.shipfast.common.Money;
import com.shipfast.order.Order;
import com.shipfast.order.OrderLine;
import com.shipfast.order.OrderRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Finance's end-of-day sales report. A "day" is a calendar day in the store's home time zone,
 * regardless of where the customer is.
 */
public class DailySalesReport {

    private final OrderRepository orders;
    private final ZoneId storeZone;

    public DailySalesReport(OrderRepository orders, ZoneId storeZone) {
        this.orders = orders;
        this.storeZone = storeZone;
    }

    public SalesSummary forDate(LocalDate date) {
        List<Order> placed = ordersPlacedOn(date);

        Money gross = Money.ZERO;
        Money refunds = Money.ZERO;
        int units = 0;
        Map<Category, Money> byCategory = new EnumMap<>(Category.class);
        for (Order order : placed) {
            gross = gross.plus(order.getTotal());
            refunds = refunds.plus(order.getRefunded());
            for (OrderLine line : order.getLines()) {
                units += line.quantity();
                byCategory.merge(line.product().getCategory(), line.lineTotal(), Money::plus);
            }
        }
        Money average = placed.isEmpty() ? Money.ZERO : gross.dividedBy(placed.size());
        return new SalesSummary(date, placed.size(), units, gross, refunds, gross.minus(refunds), average, byCategory);
    }

    List<Order> ordersPlacedOn(LocalDate date) {
        Instant start = date.atStartOfDay(storeZone).toInstant();
        Instant end = date.atTime(LocalTime.of(23, 59, 59)).atZone(storeZone).toInstant();
        return orders.findAll().stream()
                .filter(order -> order.getStatus().countsAsRevenue())
                .filter(order -> !order.getPlacedAt().isBefore(start) && !order.getPlacedAt().isAfter(end))
                .toList();
    }
}
