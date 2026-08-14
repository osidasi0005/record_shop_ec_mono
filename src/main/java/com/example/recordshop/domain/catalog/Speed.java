package com.example.recordshop.domain.catalog;

/**
 * 回転数。カセット・CD など回転数の概念がない媒体では {@link #NOT_APPLICABLE} を使う。
 */
public enum Speed {
    RPM_33,
    RPM_45,
    RPM_78,
    NOT_APPLICABLE
}
