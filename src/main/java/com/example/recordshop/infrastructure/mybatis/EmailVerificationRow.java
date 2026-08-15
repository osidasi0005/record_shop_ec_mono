package com.example.recordshop.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

/** email_verificationsテーブルの1行に対応するDTO。ドメインの{@code EmailVerification}とは別物。 */
public record EmailVerificationRow(
        UUID id, String email, String passwordHash, String displayName,
        String verificationCode, Instant expiresAt, int attemptCount
) {
}
