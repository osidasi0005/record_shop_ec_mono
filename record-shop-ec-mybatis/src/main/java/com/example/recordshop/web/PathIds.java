package com.example.recordshop.web;

import com.example.recordshop.domain.shared.MalformedIdentifierException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Function;

/**
 * 画面系コントローラ({@code @Controller})が、URLのパス変数・リクエストパラメータで受け取った
 * ID文字列をID値オブジェクトへ変換するためのヘルパー。
 *
 * <p>形式不正なIDは「そのリソースは存在しない」として 404 Not Found に変換する。
 * {@link ApiExceptionHandler} は {@code @RestControllerAdvice} のため、変換を経由せずに
 * {@link MalformedIdentifierException} が伝播すると画面リクエストにもJSONが返ってしまう。
 * ここで {@link ResponseStatusException} に包み直すことで、画面側はSpring標準のエラーページになる。
 */
public final class PathIds {

    private PathIds() {
    }

    /**
     * @param raw          URLから受け取った生のID文字列
     * @param factory      {@code ReleaseId::of} などのファクトリ
     * @param resourceName 404メッセージに載せるリソース名
     */
    public static <T> T parse(String raw, Function<String, T> factory, String resourceName) {
        try {
            return factory.apply(raw);
        } catch (MalformedIdentifierException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, resourceName + " not found: " + raw);
        }
    }
}
