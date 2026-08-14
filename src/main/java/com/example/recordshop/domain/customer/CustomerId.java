package com.example.recordshop.domain.customer;

import java.util.Objects;
import java.util.UUID;

/**
 * Customer を一意に識別する ID。
 *
 * <p>この比較実験プロジェクトでは Customer 集約そのもの(フェーズ1では未移植)は無いが、
 * Ordering コンテキストが customerId を「他集約への参照」として持つために ID だけ先行コピーしている。
 * Ordering/Payment コンテキストからは、このIDのみを参照する(集約をまたいだ参照はID経由が原則)。
 */
public record CustomerId(UUID value) {

    public CustomerId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static CustomerId generate() {
        return new CustomerId(UUID.randomUUID());
    }

    public static CustomerId of(String uuid) {
        return new CustomerId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
