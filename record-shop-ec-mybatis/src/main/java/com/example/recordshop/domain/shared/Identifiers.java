package com.example.recordshop.domain.shared;

import java.util.UUID;

/**
 * ID値オブジェクトの {@code of(String)} ファクトリが共通で使うUUID変換ユーティリティ。
 *
 * <p>{@link UUID#fromString} は形式不正で {@link IllegalArgumentException}、null で
 * {@link NullPointerException} を投げるが、どちらも「指定されたIDのリソースは存在しない」という
 * 同じ意味しか持たないため、{@link MalformedIdentifierException} に統一する。
 */
public final class Identifiers {

    private Identifiers() {
    }

    public static UUID parse(String uuid, String idTypeName) {
        if (uuid == null) {
            throw new MalformedIdentifierException(idTypeName + " が指定されていません");
        }
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            throw new MalformedIdentifierException(idTypeName + " の形式が不正です: " + uuid);
        }
    }
}
