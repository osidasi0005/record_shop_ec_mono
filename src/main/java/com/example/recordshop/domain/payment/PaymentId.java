package com.example.recordshop.domain.payment;

import com.example.recordshop.domain.shared.Identifiers;

import java.util.Objects;
import java.util.UUID;

/**
 * {@link Payment} を一意に識別する ID。
 */
public record PaymentId(UUID value) {

    public PaymentId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static PaymentId generate() {
        return new PaymentId(UUID.randomUUID());
    }

    public static PaymentId of(String uuid) {
        return new PaymentId(Identifiers.parse(uuid, "PaymentId"));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
