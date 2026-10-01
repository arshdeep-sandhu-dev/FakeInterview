package com.shipfast.order;

import java.util.List;

/**
 * What the storefront sends when a shopper clicks "Place order".
 *
 * @param couponCode optional; {@code null} or blank if the shopper did not enter one
 */
public record OrderRequest(String customerId, List<Item> items, String couponCode) {

    public record Item(String sku, int quantity) {
    }

    public static OrderRequest of(String customerId, Item... items) {
        return new OrderRequest(customerId, List.of(items), null);
    }

    public OrderRequest withCoupon(String code) {
        return new OrderRequest(customerId, items, code);
    }

    public boolean hasCoupon() {
        return couponCode != null && !couponCode.isBlank();
    }
}
