package com.example.recordshop.web.catalog;

import com.example.recordshop.domain.catalog.Pressing;

public record PressingResponse(
        String pressingId,
        String labelName,
        String catalogNumber,
        String country,
        int pressYear,
        boolean reissue
) {
    public static PressingResponse from(Pressing pressing) {
        return new PressingResponse(
                pressing.pressingId().toString(),
                pressing.labelName(),
                pressing.catalogNumber(),
                pressing.country(),
                pressing.pressYear(),
                pressing.isReissue()
        );
    }
}
