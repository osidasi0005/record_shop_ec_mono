package com.example.recordshop.domain.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AddressTest {

    private Address address(String country) {
        return new Address("山田 太郎", "150-0001", "東京都", "渋谷区", "1-2-3", country);
    }

    @Test
    void ISO3166_1_alpha2の2文字なら受け付ける() {
        assertDoesNotThrow(() -> address("JP"));
        assertDoesNotThrow(() -> address("US"));
    }

    @Test
    void 国名を書いた3文字以上はドメイン側で弾く() {
        // DBのカラムは VARCHAR(2)。ドメインで検証しないとINSERT時まで素通りし、
        // 利用者には500エラーとしてしか見えなくなる。
        assertThatThrownBy(() -> address("Japan"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Japan");
    }

    @Test
    void 小文字や1文字も受け付けない() {
        assertThatThrownBy(() -> address("jp")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> address("J")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 空文字はこれまでどおりblankとして弾く() {
        assertThatThrownBy(() -> address(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void 有効な国コードはそのまま保持される() {
        assertThat(address("GB").country()).isEqualTo("GB");
    }
}
