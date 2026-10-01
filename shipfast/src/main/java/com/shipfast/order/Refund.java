package com.shipfast.order;

import com.shipfast.common.Money;
import java.time.Instant;

public record Refund(String id, Money amount, String reason, Instant createdAt) {
}
