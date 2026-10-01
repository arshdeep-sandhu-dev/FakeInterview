package com.shipfast.inventory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Warehouse stock ledger.
 *
 * <p>Stock moves through three buckets: {@code available -> reserved -> sold}. Checkout reserves
 * stock before charging the customer, then either commits it (payment captured) or releases it
 * back to the shelf (payment failed).
 */
public class InventoryService {

    public static final int DEFAULT_LOW_STOCK_THRESHOLD = 10;

    private final Map<String, Integer> received = new ConcurrentHashMap<>();
    private final Map<String, Integer> available = new ConcurrentHashMap<>();
    private final Map<String, Integer> reserved = new ConcurrentHashMap<>();
    private final Map<String, Integer> sold = new ConcurrentHashMap<>();
    private final List<LowStockListener> lowStockListeners = new CopyOnWriteArrayList<>();
    private final int lowStockThreshold;

    public InventoryService() {
        this(DEFAULT_LOW_STOCK_THRESHOLD);
    }

    public InventoryService(int lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }

    public void onLowStock(LowStockListener listener) {
        lowStockListeners.add(listener);
    }

    /** Records a delivery from a supplier. */
    public void receive(String sku, int quantity) {
        requirePositive(quantity);
        received.merge(sku, quantity, Integer::sum);
        available.merge(sku, quantity, Integer::sum);
    }

    /**
     * Holds {@code quantity} units for an order.
     *
     * @return false if there is not enough available stock; nothing is reserved in that case
     */
    public boolean reserve(String sku, int quantity) {
        requirePositive(quantity);
        Integer onShelf = available.get(sku);
        if (onShelf == null || onShelf < quantity) {
            return false;
        }
        int remaining = onShelf - quantity;
        if (remaining <= lowStockThreshold) {
            lowStockListeners.forEach(listener -> listener.onLowStock(sku, remaining));
        }
        available.put(sku, remaining);
        reserved.merge(sku, quantity, Integer::sum);
        return true;
    }

    /** Returns reserved units to the shelf, e.g. when payment is declined. */
    public void release(String sku, int quantity) {
        requirePositive(quantity);
        int held = reserved.getOrDefault(sku, 0);
        if (quantity > held) {
            throw new IllegalStateException(
                    "Cannot release " + quantity + " x " + sku + "; only " + held + " reserved");
        }
        reserved.put(sku, held - quantity);
        available.merge(sku, quantity, Integer::sum);
    }

    /** Marks reserved units as sold once payment has been captured. */
    public void commit(String sku, int quantity) {
        requirePositive(quantity);
        int held = reserved.getOrDefault(sku, 0);
        if (quantity > held) {
            throw new IllegalStateException(
                    "Cannot commit " + quantity + " x " + sku + "; only " + held + " reserved");
        }
        reserved.put(sku, held - quantity);
        sold.merge(sku, quantity, Integer::sum);
    }

    public int available(String sku) {
        return available.getOrDefault(sku, 0);
    }

    public int reserved(String sku) {
        return reserved.getOrDefault(sku, 0);
    }

    public int sold(String sku) {
        return sold.getOrDefault(sku, 0);
    }

    public StockSnapshot snapshot(String sku) {
        return new StockSnapshot(
                sku,
                received.getOrDefault(sku, 0),
                available(sku),
                reserved(sku),
                sold(sku));
    }

    public Set<String> skus() {
        return Set.copyOf(received.keySet());
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, was " + quantity);
        }
    }
}
