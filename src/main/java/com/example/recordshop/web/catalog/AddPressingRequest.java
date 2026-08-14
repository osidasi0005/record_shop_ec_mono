package com.example.recordshop.web.catalog;

import com.example.recordshop.domain.catalog.MediaType;
import com.example.recordshop.domain.catalog.Speed;

/** POST /api/releases/{releaseId}/pressings のリクエストボディ。 */
public record AddPressingRequest(
        String labelName,
        String catalogNumber,
        String country,
        int pressYear,
        String matrixRunout,
        boolean reissue,
        MediaType mediaType,
        Speed speed,
        int discCount
) {
}
