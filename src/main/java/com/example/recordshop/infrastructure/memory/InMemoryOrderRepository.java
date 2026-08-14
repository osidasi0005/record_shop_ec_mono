package com.example.recordshop.infrastructure.memory;

import com.example.recordshop.domain.customer.CustomerId;
import com.example.recordshop.domain.ordering.Order;
import com.example.recordshop.domain.ordering.OrderId;
import com.example.recordshop.domain.ordering.OrderRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryOrderRepository implements OrderRepository {

    private final Map<OrderId, Order> store = new LinkedHashMap<>();

    @Override
    public void save(Order order) {
        store.put(order.orderId(), order);
    }

    @Override
    public Optional<Order> findById(OrderId orderId) {
        return Optional.ofNullable(store.get(orderId));
    }

    @Override
    public List<Order> findByCustomerId(CustomerId customerId) {
        return store.values().stream().filter(o -> o.customerId().equals(customerId)).toList();
    }
}
