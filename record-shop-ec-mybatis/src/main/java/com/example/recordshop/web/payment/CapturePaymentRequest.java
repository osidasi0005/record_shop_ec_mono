package com.example.recordshop.web.payment;

import com.example.recordshop.domain.payment.PaymentMethod;

/** POST /api/orders/{orderId}/payments のリクエストボディ。 */
public record CapturePaymentRequest(PaymentMethod method) {
}
