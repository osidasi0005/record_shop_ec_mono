package com.example.recordshop.domain.payment;

import com.example.recordshop.domain.ordering.OrderId;

import java.util.List;
import java.util.Optional;

/**
 * Payment 集約の永続化ポート(インターフェースのみ)。
 */
public interface PaymentRepository {

    void save(Payment payment);

    Optional<Payment> findById(PaymentId paymentId);

    List<Payment> findByOrderId(OrderId orderId);
}
