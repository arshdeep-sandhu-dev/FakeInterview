package com.shipfast.catalog;

import com.shipfast.common.Money;
import java.time.Instant;

public record PriceChange(String sku, Money oldPrice, Money newPrice, Instant changedAt) {
}
