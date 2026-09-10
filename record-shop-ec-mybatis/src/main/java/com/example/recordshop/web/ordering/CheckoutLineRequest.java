package com.example.recordshop.web.ordering;

import com.example.recordshop.domain.inventory.ConditionType;

/** カート1行分。Cart 自体は永続化せず、チェックアウトの都度この内容から組み立てる。 */
public record CheckoutLineRequest(
        String listingId,
        ConditionType conditionType,
        int quantity
) {
}
