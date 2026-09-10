package com.example.recordshop.domain.customer;

import java.time.Instant;
import java.util.Objects;

/**
 * 会員(Customer)。Customer コンテキストの集約ルート。
 *
 * <p>パスワードのハッシュ化自体は暗号技術的な関心事なのでドメイン層では行わず、
 * 既にハッシュ化済みの文字列を受け取って保持するだけにとどめる(実際のハッシュ化は
 * {@link PasswordHasher} ポート経由でアプリケーション層/ドメインサービスが行う)。
 *
 * <p><b>不変条件</b>
 * <ul>
 *   <li>Email はシステム全体で一意(この集約単体では強制できないため、
 *       {@link CustomerRegistrationService} が登録時に repository 経由で重複チェックする)</li>
 * </ul>
 */
public final class Customer {

    private final CustomerId customerId;
    private final Email email;
    private String passwordHash;
    private String displayName;
    private final CustomerRole role;
    private final Instant registeredAt;

    private Customer(CustomerId customerId, Email email, String passwordHash, String displayName,
                      CustomerRole role, Instant registeredAt) {
        this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.passwordHash = requireNonBlank(passwordHash, "passwordHash");
        this.displayName = requireNonBlank(displayName, "displayName");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt must not be null");
    }

    /** 一般会員として自己登録する(role は常に CUSTOMER)。ADMIN は {@link #reconstitute} 経由でのみ復元される。 */
    public static Customer register(CustomerId customerId, Email email, String passwordHash, String displayName,
                                     Instant registeredAt) {
        return new Customer(customerId, email, passwordHash, displayName, CustomerRole.CUSTOMER, registeredAt);
    }

    /**
     * 永続化層からの再構築用ファクトリ。リポジトリ実装(インフラ層)から呼ばれる想定。
     */
    public static Customer reconstitute(CustomerId customerId, Email email, String passwordHash, String displayName,
                                         CustomerRole role, Instant registeredAt) {
        return new Customer(customerId, email, passwordHash, displayName, role, registeredAt);
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public void changeDisplayName(String newDisplayName) {
        this.displayName = requireNonBlank(newDisplayName, "displayName");
    }

    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = requireNonBlank(newPasswordHash, "passwordHash");
    }

    public CustomerId customerId() {
        return customerId;
    }

    public Email email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String displayName() {
        return displayName;
    }

    public CustomerRole role() {
        return role;
    }

    public Instant registeredAt() {
        return registeredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Customer other)) return false;
        return customerId.equals(other.customerId);
    }

    @Override
    public int hashCode() {
        return customerId.hashCode();
    }

    @Override
    public String toString() {
        return "Customer{%s, %s, %s}".formatted(customerId, email, displayName);
    }
}
