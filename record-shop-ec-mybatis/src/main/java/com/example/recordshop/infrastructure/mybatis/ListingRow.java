package com.example.recordshop.infrastructure.mybatis;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * listingsテーブルの1行に対応するDTO。ドメインの{@code Listing}とは別物。
 * {@code version}は楽観ロック用の列で、MyBatisでは自動管理されないため
 * {@link MyBatisListingRepository}が手動で扱う。
 */
public record ListingRow(UUID id, UUID pressingId, String conditionType, BigDecimal priceAmount,
                          String priceCurrency, String status, Integer stockQuantity,
                          String vinylGrade, String sleeveGrade, String sellerNote, long version) {
}
