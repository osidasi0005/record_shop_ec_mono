package com.example.recordshop.domain.shared;

import java.util.Objects;

/**
 * 配送先・請求先を表す住所の値オブジェクト。
 *
 * <p>{@link com.example.recordshop.domain.ordering.Order} は ShippingAddress / BillingAddress として
 * それぞれ独立した Address インスタンスを保持する。値オブジェクトなので変更は「差し替え」のみで、
 * フィールド単位のミューテーションは提供しない。
 */
public record Address(
        String recipientName,
        String postalCode,
        String prefecture,
        String city,
        String addressLine,
        String country
) {
    public Address {
        requireNonBlank(recipientName, "recipientName");
        requireNonBlank(postalCode, "postalCode");
        requireNonBlank(prefecture, "prefecture");
        requireNonBlank(city, "city");
        requireNonBlank(addressLine, "addressLine");
        CountryCodes.requireValid(country, "country");
    }

    private static void requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
