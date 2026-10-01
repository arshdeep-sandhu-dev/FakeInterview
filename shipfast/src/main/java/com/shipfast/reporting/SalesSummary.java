package com.shipfast.reporting;

import com.shipfast.catalog.Category;
import com.shipfast.common.Money;
import java.time.LocalDate;
import java.util.Map;

public record SalesSummary(
        LocalDate date,
        int orderCount,
        int unitsSold,
        Money grossSales,
        Money refunds,
        Money netSales,
        Money averageOrderValue,
        Map<Category, Money> salesByCategory) {

    public String format() {
        StringBuilder out = new StringBuilder()
                .append("Daily sales ").append(date).append('\n')
                .append(String.format("  %-20s %12d%n", "Orders", orderCount))
                .append(String.format("  %-20s %12d%n", "Units", unitsSold))
                .append(String.format("  %-20s %12s%n", "Gross sales", grossSales))
                .append(String.format("  %-20s %12s%n", "Refunds", refunds))
                .append(String.format("  %-20s %12s%n", "Net sales", netSales))
                .append(String.format("  %-20s %12s%n", "Avg order value", averageOrderValue));
        salesByCategory.forEach((category, sales) ->
                out.append(String.format("    %-18s %12s%n", category, sales)));
        return out.toString();
    }
}
