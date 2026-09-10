package com.example.recordshop.domain.customer;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * メールアドレスの値オブジェクト。ログインIDを兼ねる。
 *
 * <p>大文字小文字の差異でアカウントが分裂しないよう、常に小文字化して保持する。
 */
public record Email(String value) {

    // 簡易的な形式チェック(RFC完全準拠は狙わず、明らかな入力ミスだけ弾く)
    private static final Pattern PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public Email {
        Objects.requireNonNull(value, "value must not be null");
        value = value.trim().toLowerCase();
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("email format is invalid: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
