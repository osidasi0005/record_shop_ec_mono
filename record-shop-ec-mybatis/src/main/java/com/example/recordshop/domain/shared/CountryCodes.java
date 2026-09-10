package com.example.recordshop.domain.shared;

import java.util.regex.Pattern;

/**
 * 国コード(ISO 3166-1 alpha-2)の検証。
 *
 * <p>住所({@link Address})・プレス版({@code Pressing})のいずれも国を2文字で保持する
 * (DBのカラムも {@code VARCHAR(2)})。ドメイン側で桁数を検証しないと、{@code Japan} のような
 * 入力がDBのINSERT時まで素通りし、利用者には500エラーとしてしか見えなくなる。
 */
public final class CountryCodes {

    private static final Pattern ALPHA2 = Pattern.compile("[A-Z]{2}");

    private CountryCodes() {
    }

    public static String requireValid(String country, String field) {
        if (country == null || country.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (!ALPHA2.matcher(country).matches()) {
            throw new IllegalArgumentException(
                    "国コードはISO 3166-1 alpha-2形式の大文字2文字で入力してください(例: JP): " + country);
        }
        return country;
    }
}
