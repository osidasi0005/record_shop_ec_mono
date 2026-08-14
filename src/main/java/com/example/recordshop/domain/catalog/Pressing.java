package com.example.recordshop.domain.catalog;

import java.util.Objects;

/**
 * プレス版。{@link Release}(作品) の子エンティティ。
 * 同じ作品でもレーベル品番・製造国・製造年が異なれば別の Pressing として扱う。
 *
 * <p>Release 集約の内部でのみ生成・変更され、外部からは {@link PressingId} 経由でのみ参照される。
 */
public final class Pressing {

    private final PressingId pressingId;
    private final String labelName;
    private final String catalogNumber;
    private final String country;
    private final int pressYear;
    private final String matrixRunout;
    private final boolean reissue;
    private final Format format;

    /**
     * 永続化層からの再構築用ファクトリ。既存の {@link PressingId} をそのまま使う(新規発行しない)点が
     * {@link Release#addPressing} との違い。リポジトリ実装(インフラ層)から呼ばれる想定。
     */
    public static Pressing reconstitute(PressingId pressingId, String labelName, String catalogNumber,
                                         String country, int pressYear, String matrixRunout,
                                         boolean reissue, Format format) {
        return new Pressing(pressingId, labelName, catalogNumber, country, pressYear, matrixRunout, reissue, format);
    }

    Pressing(PressingId pressingId, String labelName, String catalogNumber, String country,
             int pressYear, String matrixRunout, boolean reissue, Format format) {
        this.pressingId = Objects.requireNonNull(pressingId, "pressingId must not be null");
        this.labelName = requireNonBlank(labelName, "labelName");
        this.catalogNumber = requireNonBlank(catalogNumber, "catalogNumber");
        this.country = requireNonBlank(country, "country");
        this.pressYear = requirePlausibleYear(pressYear);
        this.matrixRunout = matrixRunout;
        this.reissue = reissue;
        this.format = Objects.requireNonNull(format, "format must not be null");
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static int requirePlausibleYear(int year) {
        // 1877 = 蓄音機(フォノグラフ)発明年。それより前のプレス年は現実的にありえない。
        if (year < 1877 || year > 2100) {
            throw new IllegalArgumentException("pressYear is not plausible: " + year);
        }
        return year;
    }

    /** Release 内でのプレス版の一意性判定に使うキー(品番 + 製造国 + 製造年)。 */
    String identityKey() {
        return catalogNumber + "|" + country + "|" + pressYear;
    }

    public PressingId pressingId() {
        return pressingId;
    }

    public String labelName() {
        return labelName;
    }

    public String catalogNumber() {
        return catalogNumber;
    }

    public String country() {
        return country;
    }

    public int pressYear() {
        return pressYear;
    }

    public String matrixRunout() {
        return matrixRunout;
    }

    public boolean isReissue() {
        return reissue;
    }

    public Format format() {
        return format;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Pressing other)) return false;
        return pressingId.equals(other.pressingId);
    }

    @Override
    public int hashCode() {
        return pressingId.hashCode();
    }

    @Override
    public String toString() {
        return "Pressing{%s, %s, %s, %d%s}".formatted(
                labelName, catalogNumber, country, pressYear, reissue ? ", reissue" : "");
    }
}
