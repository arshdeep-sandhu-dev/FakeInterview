package com.shipfast.order;

import com.shipfast.catalog.Product;
import com.shipfast.common.Money;
import java.util.Objects;

public class OrderLine {

    private final Product product;
    private final int quantity;

    public OrderLine(Product product, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.product = Objects.requireNonNull(product, "product");
        this.quantity = quantity;
    }

    public Product product() {
        return product;
    }

    public String sku() {
        return product.getSku();
    }

    public int quantity() {
        return quantity;
    }

    public Money unitPrice() {
        return product.getPrice();
    }

    public Money lineTotal() {
        return unitPrice().times(quantity);
    }

    @Override
    public String toString() {
        return quantity + " x " + sku() + " @ " + unitPrice();
    }
}
