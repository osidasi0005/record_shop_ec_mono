package com.example.recordshop.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

/**
 * ordersテーブルの1行に対応するDTO。ドメインの{@code Order}とは別物。
 * 住所(Address値オブジェクト)は列展開して持つ。
 */
public record OrderRow(
        UUID id, UUID customerId, String status, Instant placedAt,
        String shipRecipientName, String shipPostalCode, String shipPrefecture,
        String shipCity, String shipAddressLine, String shipCountry,
        String billRecipientName, String billPostalCode, String billPrefecture,
        String billCity, String billAddressLine, String billCountry
) {
}
