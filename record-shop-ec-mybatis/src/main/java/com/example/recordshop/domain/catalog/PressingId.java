package com.example.recordshop.domain.catalog;

import com.example.recordshop.domain.shared.Identifiers;

import java.util.Objects;
import java.util.UUID;

/**
 * {@link Pressing}(プレス版) を一意に識別する ID。
 * Release 集約の内部エンティティだが、Inventory / Ordering コンテキストからは
 * この ID のみを参照する(集約をまたいだ参照は ID 経由が原則)。
 */
public record PressingId(UUID value) {

    public PressingId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static PressingId generate() {
        return new PressingId(UUID.randomUUID());
    }

    public static PressingId of(String uuid) {
        return new PressingId(Identifiers.parse(uuid, "PressingId"));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
