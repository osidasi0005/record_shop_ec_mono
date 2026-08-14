package com.example.recordshop.infrastructure.mybatis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * paymentsテーブルの1行に対応するDTO。ドメインの{@code Payment}とは別物。
 * capturedAtはPENDING状態ではnullになりうる(Payment#capturedAt()がOptionalな理由と同じ)。
 */
public record PaymentRow(
        UUID id, UUID orderId, BigDecimal amountAmount, String amountCurrency,
        String method, String status, Instant capturedAt
) {
}
