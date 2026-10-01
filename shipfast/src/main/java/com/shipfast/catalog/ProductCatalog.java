package com.shipfast.catalog;

import com.shipfast.common.Money;
import com.shipfast.common.NotFoundException;
import com.shipfast.common.ValidationException;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** The single source of truth for products and their current prices. */
public class ProductCatalog {

    private final Map<String, Product> products = new ConcurrentHashMap<>();
    private final List<PriceChange> priceHistory = new CopyOnWriteArrayList<>();
    private final Clock clock;

    public ProductCatalog(Clock clock) {
        this.clock = clock;
    }

    public Product add(Product product) {
        Product existing = products.putIfAbsent(product.getSku(), product);
        if (existing != null) {
            throw new ValidationException("Duplicate SKU " + product.getSku());
        }
        return product;
    }

    public Optional<Product> find(String sku) {
        return Optional.ofNullable(products.get(sku));
    }

    public Product require(String sku) {
        return find(sku).orElseThrow(() -> new NotFoundException("No product with SKU " + sku));
    }

    public List<Product> all() {
        return products.values().stream()
                .sorted(Comparator.comparing(Product::getSku))
                .toList();
    }

    public List<Product> inCategory(Category category) {
        return all().stream()
                .filter(p -> p.getCategory() == category)
                .toList();
    }

    /** Changes the list price of a product. Takes effect for all subsequent quotes. */
    public void updatePrice(String sku, Money newPrice) {
        if (newPrice == null || !newPrice.isPositive()) {
            throw new ValidationException("Price must be positive");
        }
        Product product = require(sku);
        Money oldPrice = product.getPrice();
        product.setPrice(newPrice);
        priceHistory.add(new PriceChange(sku, oldPrice, newPrice, clock.instant()));
    }

    public List<PriceChange> priceHistory(String sku) {
        return priceHistory.stream()
                .filter(change -> change.sku().equals(sku))
                .toList();
    }

    public void discontinue(String sku) {
        require(sku).deactivate();
    }
}
