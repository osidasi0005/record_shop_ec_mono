package com.example.recordshop.web.catalog;

import java.util.Set;

/** POST /api/releases のリクエストボディ。 */
public record RegisterReleaseRequest(
        String title,
        String artistName,
        Set<String> genres,
        int originalReleaseYear,
        String artworkUrl
) {
}
