package com.example.recordshop.infrastructure.mybatis;

import java.util.UUID;

/** releasesテーブルの1行に対応するDTO。ドメインの{@code Release}とは別物。 */
public record ReleaseRow(UUID id, String title, String artistName, int originalReleaseYear, String artworkUrl) {
}
