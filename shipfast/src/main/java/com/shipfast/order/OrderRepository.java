package com.shipfast.order;

import com.shipfast.common.NotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class OrderRepository {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();

    public void save(Order order) {
        orders.put(order.getId(), order);
    }

    public Optional<Order> find(String id) {
        return Optional.ofNullable(orders.get(id));
    }

    public Order require(String id) {
        return find(id).orElseThrow(() -> new NotFoundException("No order " + id));
    }

    /** All orders, oldest first. */
    public List<Order> findAll() {
        return orders.values().stream()
                .sorted(Comparator.comparing(Order::getPlacedAt).thenComparing(Order::getId))
                .toList();
    }

    public List<Order> findByCustomer(String customerId) {
        return findAll().stream()
                .filter(order -> order.getCustomerId().equals(customerId))
                .toList();
    }

    public int count() {
        return orders.size();
    }
}
