package com.example.recordshop.web.inventory;

import com.example.recordshop.domain.inventory.ConditionType;
import com.example.recordshop.domain.inventory.GoldmineGrade;

import java.math.BigDecimal;

/**
 * POST /api/listings のリクエストボディ。
 *
 * <p>conditionType=NEW のときは initialStock が必須(vinylGrade/sleeveGrade/sellerNote は無視)。
 * conditionType=USED のときは vinylGrade/sleeveGrade が必須(initialStock は無視、数量は常に1)。
 */
public record CreateListingRequest(
        String pressingId,
        ConditionType conditionType,
        BigDecimal priceAmount,
        String priceCurrency,
        Integer initialStock,
        GoldmineGrade vinylGrade,
        GoldmineGrade sleeveGrade,
        String sellerNote
) {
}
