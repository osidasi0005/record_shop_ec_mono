package com.example.recordshop.web.ordering;

import com.example.recordshop.domain.inventory.ConditionType;
import com.example.recordshop.domain.shared.Money;

/** カート画面表示用の1行分ビューモデル(カタログ情報とCartLineを合成したもの)。 */
public record CartLineView(
        String listingId,
        String releaseTitle,
        String artistName,
        String catalogNumber,
        ConditionType conditionType,
        Money unitPrice,
        int quantity,
        Money lineTotal
) {
}
