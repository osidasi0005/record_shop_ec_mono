package com.example.recordshop.infrastructure.mybatis;

import java.util.UUID;

/** release_genresテーブルの1行に対応するDTO。 */
public record GenreRow(UUID releaseId, String genre) {
}
