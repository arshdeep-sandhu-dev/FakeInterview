package com.shipfast.customer;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class CustomerRepository {

    private final Map<String, Customer> customers = new ConcurrentHashMap<>();

    public Customer save(Customer customer) {
        customers.put(customer.id(), customer);
        return customer;
    }

    public Optional<Customer> find(String id) {
        return Optional.ofNullable(customers.get(id));
    }

    public Collection<Customer> all() {
        return List.copyOf(customers.values());
    }
}
