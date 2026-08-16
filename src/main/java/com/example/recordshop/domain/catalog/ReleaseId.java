package com.example.recordshop.domain.catalog;

import com.example.recordshop.domain.shared.Identifiers;

import java.util.Objects;
import java.util.UUID;

/**
 * {@link Release} を一意に識別する ID。
 */
public record ReleaseId(UUID value) {

    public ReleaseId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static ReleaseId generate() {
        return new ReleaseId(UUID.randomUUID());
    }

    public static ReleaseId of(String uuid) {
        return new ReleaseId(Identifiers.parse(uuid, "ReleaseId"));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
