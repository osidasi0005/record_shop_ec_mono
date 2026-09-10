package com.example.recordshop.domain.customer;

import java.util.Objects;
import java.util.UUID;

/**
 * EmailVerification を一意に識別する ID。
 *
 * <p>Customer とは別集約であるため、CustomerId とは別に専用の ID VO を持つ
 * (集約ごとに専用ID VOを持つ、という既存の慣習に合わせる)。
 */
public record EmailVerificationId(UUID value) {

    public EmailVerificationId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static EmailVerificationId generate() {
        return new EmailVerificationId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
