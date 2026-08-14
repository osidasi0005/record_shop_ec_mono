package com.example.recordshop.domain.customer;

/**
 * 会員の権限区分。
 *
 * <p>「歩く骨格」フェーズでは出品者(Seller)を独立した集約にはせず、
 * Customer に ADMIN ロールを持たせることで管理画面アクセスを表現する簡易実装とする。
 * 本格運用するなら Seller/Staff を別集約に分離すべき箇所。
 */
public enum CustomerRole {
    CUSTOMER,
    ADMIN
}
