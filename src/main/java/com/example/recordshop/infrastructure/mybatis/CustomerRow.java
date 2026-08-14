package com.example.recordshop.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

/** customersテーブルの1行に対応するDTO。ドメインの{@code Customer}とは別物。 */
public record CustomerRow(
        UUID id, String email, String passwordHash, String displayName, String role, Instant registeredAt
) {
}
