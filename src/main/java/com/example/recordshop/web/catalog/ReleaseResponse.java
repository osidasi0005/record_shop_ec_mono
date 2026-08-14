package com.example.recordshop.web.catalog;

import com.example.recordshop.domain.catalog.Release;

import java.util.List;
import java.util.Set;

public record ReleaseResponse(
        String releaseId,
        String title,
        String artistName,
        Set<String> genres,
        int originalReleaseYear,
        List<PressingResponse> pressings
) {
    public static ReleaseResponse from(Release release) {
        return new ReleaseResponse(
                release.releaseId().toString(),
                release.title(),
                release.artistName(),
                release.genres(),
                release.originalReleaseYear(),
                release.pressings().stream().map(PressingResponse::from).toList()
        );
    }
}
