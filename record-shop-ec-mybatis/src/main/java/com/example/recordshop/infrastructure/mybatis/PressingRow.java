package com.example.recordshop.infrastructure.mybatis;

import java.util.UUID;

/** pressingsテーブルの1行に対応するDTO。ドメインの{@code Pressing}とは別物。 */
public record PressingRow(UUID id, UUID releaseId, String labelName, String catalogNumber, String country,
                           int pressYear, String matrixRunout, boolean reissue,
                           String mediaType, String speed, int discCount, String artworkUrl) {
}
