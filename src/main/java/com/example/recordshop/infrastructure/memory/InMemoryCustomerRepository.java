package com.example.recordshop.infrastructure.memory;

import com.example.recordshop.domain.customer.Customer;
import com.example.recordshop.domain.customer.CustomerId;
import com.example.recordshop.domain.customer.CustomerRepository;
import com.example.recordshop.domain.customer.Email;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryCustomerRepository implements CustomerRepository {

    private final Map<CustomerId, Customer> store = new LinkedHashMap<>();

    @Override
    public void save(Customer customer) {
        store.put(customer.customerId(), customer);
    }

    @Override
    public Optional<Customer> findById(CustomerId customerId) {
        return Optional.ofNullable(store.get(customerId));
    }

    @Override
    public Optional<Customer> findByEmail(Email email) {
        return store.values().stream().filter(c -> c.email().equals(email)).findFirst();
    }

    @Override
    public boolean existsByEmail(Email email) {
        return findByEmail(email).isPresent();
    }
}
