package com.example.recordshop.infrastructure.mybatis;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * order_linesテーブルの1行に対応するDTO。ドメインの{@code OrderLine}とは別物。
 * PressingSnapshotは確定時点の複製なので、列をそのまま展開して非正規化する(JPA版と同じ設計)。
 */
public record OrderLineRow(
        UUID orderId, UUID listingId, String releaseTitle, String artistName, String labelName,
        String catalogNumber, String pressingCountry, int pressYear, String mediaType, String speed,
        int discCount, String conditionType, String vinylGrade, String sleeveGrade,
        BigDecimal unitPriceAmount, String unitPriceCurrency, int quantity
) {
}
