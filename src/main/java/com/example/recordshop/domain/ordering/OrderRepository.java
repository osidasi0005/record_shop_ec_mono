package com.example.recordshop.domain.ordering;

import com.example.recordshop.domain.customer.CustomerId;

import java.util.List;
import java.util.Optional;

/**
 * Order 集約の永続化ポート(インターフェースのみ)。
 */
public interface OrderRepository {

    void save(Order order);

    Optional<Order> findById(OrderId orderId);

    /** 注文履歴画面用。新しい注文が先頭に来るようにする。 */
    List<Order> findByCustomerId(CustomerId customerId);
}
