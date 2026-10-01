package com.shipfast.inventory;

/**
 * Point-in-time view of a SKU's stock.
 *
 * @param received  total units ever received into the warehouse
 * @param available units on the shelf that can be reserved
 * @param reserved  units held for orders whose payment has not completed
 * @param sold      units whose payment has been captured
 */
public record StockSnapshot(String sku, int received, int available, int reserved, int sold) {

    public int accountedFor() {
        return available + reserved + sold;
    }
}
