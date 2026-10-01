package com.shipfast.catalog;

import com.shipfast.common.Money;

/**
 * A sellable item. Price and availability are managed through {@link ProductCatalog}.
 */
public class Product {

    private final String sku;
    private final String name;
    private final Category category;
    private final int weightGrams;
    private volatile Money price;
    private volatile boolean active = true;

    public Product(String sku, String name, Category category, Money price, int weightGrams) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku is required");
        }
        if (price == null || !price.isPositive()) {
            throw new IllegalArgumentException("price must be positive");
        }
        if (weightGrams < 0) {
            throw new IllegalArgumentException("weight cannot be negative");
        }
        this.sku = sku;
        this.name = name;
        this.category = category;
        this.price = price;
        this.weightGrams = weightGrams;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public Category getCategory() {
        return category;
    }

    public Money getPrice() {
        return price;
    }

    public int getWeightGrams() {
        return weightGrams;
    }

    public boolean isActive() {
        return active;
    }

    void setPrice(Money price) {
        this.price = price;
    }

    void deactivate() {
        this.active = false;
    }

    @Override
    public String toString() {
        return sku + " (" + name + ", " + price + ")";
    }
}
