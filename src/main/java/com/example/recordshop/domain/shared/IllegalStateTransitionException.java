package com.example.recordshop.domain.shared;

/**
 * 許可されていない状態遷移を試みた場合の例外。
 *
 * <p>例: Sold 済みの Used Listing を再度 Publish しようとした、
 * Shipped 済みの Order の配送先を変更しようとした、など。
 */
public class IllegalStateTransitionException extends RuntimeException {

    public IllegalStateTransitionException(String message) {
        super(message);
    }
}
