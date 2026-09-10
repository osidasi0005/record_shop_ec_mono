package com.example.recordshop.domain.ordering;

import java.util.Objects;
import java.util.UUID;

public record CartId(UUID value) {
    public CartId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static CartId generate() {
        return new CartId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
