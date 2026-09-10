package com.example.recordshop.domain.inventory;

import com.example.recordshop.domain.shared.Identifiers;

import java.util.Objects;
import java.util.UUID;

/**
 * {@link Listing} を一意に識別する ID。
 */
public record ListingId(UUID value) {

    public ListingId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static ListingId generate() {
        return new ListingId(UUID.randomUUID());
    }

    public static ListingId of(String uuid) {
        return new ListingId(Identifiers.parse(uuid, "ListingId"));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
