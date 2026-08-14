package com.example.recordshop.domain.customer;

import com.example.recordshop.domain.shared.InvariantViolationException;

import java.time.Instant;

/**
 * 会員登録のドメインサービス。
 *
 * <p>「Email がシステム全体で一意である」という不変条件は Customer 集約単体では守れない
 * (repository を横断した確認が必要)ため、{@link com.example.recordshop.domain.ordering.OrderPlacementService}
 * と同様にこの domain service が調停する。パスワードのハッシュ化も {@link PasswordHasher} ポート経由で行う。
 */
public final class CustomerRegistrationService {

    private final CustomerRepository customerRepository;
    private final PasswordHasher passwordHasher;

    public CustomerRegistrationService(CustomerRepository customerRepository, PasswordHasher passwordHasher) {
        this.customerRepository = customerRepository;
        this.passwordHasher = passwordHasher;
    }

    public Customer register(Email email, String rawPassword, String displayName, Instant now) {
        if (customerRepository.existsByEmail(email)) {
            throw new InvariantViolationException("このメールアドレスは既に登録されています: " + email);
        }

        String passwordHash = passwordHasher.hash(rawPassword);
        Customer customer = Customer.register(CustomerId.generate(), email, passwordHash, displayName, now);
        customerRepository.save(customer);
        return customer;
    }
}
