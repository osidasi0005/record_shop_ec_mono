package com.example.recordshop.domain.customer;

import java.util.Optional;

/**
 * Customer 集約の永続化ポート(インターフェースのみ)。
 */
public interface CustomerRepository {

    void save(Customer customer);

    Optional<Customer> findById(CustomerId customerId);

    Optional<Customer> findByEmail(Email email);

    boolean existsByEmail(Email email);
}
