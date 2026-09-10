package com.example.recordshop.domain.ordering;

import com.example.recordshop.domain.catalog.Format;
import com.example.recordshop.domain.inventory.ConditionType;
import com.example.recordshop.domain.inventory.GoldmineGrade;

import java.util.Objects;

/**
 * 注文確定時点の Pressing 情報を複製・凍結したスナップショット。
 *
 * <p>後日カタログ側(Release/Pressing)の情報が訂正されても、既存の {@link OrderLine} は
 * 発注当時の内容のまま変わらない。値オブジェクトなので一度生成したら変更不可。
 *
 * <p>vinylGrade / sleeveGrade は Used の場合のみ値を持ち、New の場合は null。
 */
public record PressingSnapshot(
        String releaseTitle,
        String artistName,
        String labelName,
        String catalogNumber,
        String country,
        int pressYear,
        Format format,
        ConditionType conditionType,
        GoldmineGrade vinylGrade,
        GoldmineGrade sleeveGrade
) {
    public PressingSnapshot {
        Objects.requireNonNull(releaseTitle, "releaseTitle must not be null");
        Objects.requireNonNull(artistName, "artistName must not be null");
        Objects.requireNonNull(labelName, "labelName must not be null");
        Objects.requireNonNull(catalogNumber, "catalogNumber must not be null");
        Objects.requireNonNull(country, "country must not be null");
        Objects.requireNonNull(format, "format must not be null");
        Objects.requireNonNull(conditionType, "conditionType must not be null");
        if (conditionType == ConditionType.USED) {
            Objects.requireNonNull(vinylGrade, "vinylGrade must not be null for USED");
            Objects.requireNonNull(sleeveGrade, "sleeveGrade must not be null for USED");
        }
    }
}
