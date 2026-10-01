package com.shipfast.order;

public class OutOfStockException extends OrderPlacementException {

    private final String sku;

    public OutOfStockException(String sku, int requested) {
        super("Not enough stock for " + sku + " (requested " + requested + ")");
        this.sku = sku;
    }

    public String getSku() {
        return sku;
    }
}
