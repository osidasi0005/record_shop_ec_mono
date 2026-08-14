package com.example.recordshop.domain.catalog;

/**
 * 盤のフォーマットを表す値オブジェクト。媒体種別・回転数・枚数の組。
 */
public record Format(MediaType mediaType, Speed speed, int discCount) {

    public Format {
        if (discCount < 1) {
            throw new IllegalArgumentException("discCount must be >= 1: " + discCount);
        }
    }

    public static Format vinyl(MediaType mediaType, Speed speed, int discCount) {
        return new Format(mediaType, speed, discCount);
    }
}
