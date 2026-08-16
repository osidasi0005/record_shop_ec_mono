package com.example.recordshop.domain.ordering;

import com.example.recordshop.domain.shared.Identifiers;

import java.util.Objects;
import java.util.UUID;

public record OrderId(UUID value) {
    public OrderId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static OrderId generate() {
        return new OrderId(UUID.randomUUID());
    }

    public static OrderId of(String uuid) {
        return new OrderId(Identifiers.parse(uuid, "OrderId"));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
