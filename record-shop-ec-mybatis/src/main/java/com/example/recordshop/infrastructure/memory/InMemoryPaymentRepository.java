package com.example.recordshop.infrastructure.memory;

import com.example.recordshop.domain.ordering.OrderId;
import com.example.recordshop.domain.payment.Payment;
import com.example.recordshop.domain.payment.PaymentId;
import com.example.recordshop.domain.payment.PaymentRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryPaymentRepository implements PaymentRepository {

    private final Map<PaymentId, Payment> store = new LinkedHashMap<>();

    @Override
    public void save(Payment payment) {
        store.put(payment.paymentId(), payment);
    }

    @Override
    public Optional<Payment> findById(PaymentId paymentId) {
        return Optional.ofNullable(store.get(paymentId));
    }

    @Override
    public List<Payment> findByOrderId(OrderId orderId) {
        return store.values().stream().filter(p -> p.orderId().equals(orderId)).toList();
    }
}
