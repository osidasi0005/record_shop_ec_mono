package com.example.recordshop.domain.catalog;

/**
 * 媒体の種類。アナログ盤の判型に加え、カセット・CD も同一カタログで扱えるようにしておく。
 */
public enum MediaType {
    LP,
    EP,
    SINGLE_7INCH,
    SINGLE_10INCH,
    SINGLE_12INCH,
    CASSETTE,
    CD
}
