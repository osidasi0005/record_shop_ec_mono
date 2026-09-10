package com.example.recordshop.domain.shared;

/**
 * ID文字列がUUIDとして解釈できなかった場合の例外。
 *
 * <p>URLのパス変数やリクエストパラメータで受け取ったID文字列を {@code XxxId.of(String)} で
 * 変換する際に投げられる。「そのIDのリソースは存在しない」ことを意味するため、Web層では
 * 404 Not Found へ変換する({@link com.example.recordshop.web.ApiExceptionHandler} 参照)。
 *
 * <p>{@link IllegalArgumentException} を継承しているのは、UUID変換失敗が本来
 * {@code UUID.fromString} の投げる IllegalArgumentException であり、既存の
 * {@code catch (IllegalArgumentException e)} の網から漏らさないため。
 */
public class MalformedIdentifierException extends IllegalArgumentException {

    public MalformedIdentifierException(String message) {
        super(message);
    }
}
