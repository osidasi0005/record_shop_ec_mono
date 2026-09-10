package com.example.recordshop.domain.shared;

/**
 * 集約が守るべき不変条件に違反した場合の例外。
 *
 * <p>例: 在庫数を超える予約、Used Listing に対する複数数量の指定、など
 * 「状態遷移そのものは正しいがパラメータや文脈がルール違反」なケースに使う。
 * 状態遷移自体が許可されていない場合は {@link IllegalStateTransitionException} を使う。
 */
public class InvariantViolationException extends RuntimeException {

    public InvariantViolationException(String message) {
        super(message);
    }
}
