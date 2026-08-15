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
        assertEmailAvailable(email);
        return persistNewCustomer(email, passwordHasher.hash(rawPassword), displayName, now);
    }

    /**
     * 既にハッシュ化済みのパスワードで本登録を確定する。
     * {@link EmailVerificationService} が確認コード照合後に呼び出す想定で、
     * 平文パスワードを持ち回らないためにこちらのメソッドを分けている。
     */
    public Customer registerWithHashedPassword(Email email, String passwordHash, String displayName, Instant now) {
        assertEmailAvailable(email);
        return persistNewCustomer(email, passwordHash, displayName, now);
    }

    /** 指定のEmailが未使用であることを保証する。使用済みなら{@link InvariantViolationException}を投げる。 */
    public void assertEmailAvailable(Email email) {
        if (customerRepository.existsByEmail(email)) {
            throw new InvariantViolationException("このメールアドレスは既に登録されています: " + email);
        }
    }

    private Customer persistNewCustomer(Email email, String passwordHash, String displayName, Instant now) {
        Customer customer = Customer.register(CustomerId.generate(), email, passwordHash, displayName, now);
        customerRepository.save(customer);
        return customer;
    }
}
