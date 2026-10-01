package com.shipfast.inventory;

@FunctionalInterface
public interface LowStockListener {

    void onLowStock(String sku, int remaining);
}
