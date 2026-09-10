package com.example.recordshop.web.ordering;

import java.util.List;

/**
 * POST /api/checkout のリクエストボディ。
 *
 * <p>Cart 集約は永続化しない設計とした(現状のドメイン層に CartRepository は存在せず、
 * {@code OrderPlacementService} も Cart インスタンスを直接受け取るシグネチャになっている)。
 * そのため、このリクエスト自体が「カートの中身」を直接運ぶ形にしている。
 * customerId 省略時は新規顧客として ID を発行する。
 */
public record CheckoutRequest(
        String customerId,
        List<CheckoutLineRequest> lines,
        AddressRequest shippingAddress,
        AddressRequest billingAddress
) {
}
