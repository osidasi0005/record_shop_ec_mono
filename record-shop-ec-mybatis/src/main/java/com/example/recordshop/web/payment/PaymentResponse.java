package com.example.recordshop.web.payment;

import com.example.recordshop.domain.payment.Payment;
import com.example.recordshop.domain.payment.PaymentMethod;
import com.example.recordshop.domain.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        String paymentId,
        String orderId,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        PaymentStatus status,
        Instant capturedAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.paymentId().toString(),
                payment.orderId().toString(),
                payment.amount().amount(),
                payment.amount().currency().getCurrencyCode(),
                payment.method(),
                payment.status(),
                payment.capturedAt().orElse(null)
        );
    }
}
