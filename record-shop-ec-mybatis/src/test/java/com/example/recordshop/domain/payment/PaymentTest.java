package com.example.recordshop.domain.payment;

import com.example.recordshop.domain.ordering.OrderId;
import com.example.recordshop.domain.shared.IllegalStateTransitionException;
import com.example.recordshop.domain.shared.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentTest {

    private Payment pendingPayment() {
        return Payment.initiate(PaymentId.generate(), OrderId.generate(), Money.jpy(4200), PaymentMethod.CREDIT_CARD);
    }

    @Test
    void capture_PendingからCapturedになりcapturedAtが確定する() {
        Payment payment = pendingPayment();
        Instant now = Instant.now();

        payment.capture(now);

        assertEquals(PaymentStatus.CAPTURED, payment.status());
        assertEquals(now, payment.capturedAt().orElseThrow());
    }

    @Test
    void fail_Pendingから失敗にできる() {
        Payment payment = pendingPayment();

        payment.fail();

        assertEquals(PaymentStatus.FAILED, payment.status());
    }

    @Test
    void fail_Captured済みは失敗にできない() {
        Payment payment = pendingPayment();
        payment.capture(Instant.now());

        assertThrows(IllegalStateTransitionException.class, payment::fail);
    }

    @Test
    void refund_Capturedからのみ返金できる() {
        Payment payment = pendingPayment();

        assertThrows(IllegalStateTransitionException.class, () -> payment.refund(Instant.now()));

        payment.capture(Instant.now());
        payment.refund(Instant.now());

        assertEquals(PaymentStatus.REFUNDED, payment.status());
    }

    @Test
    void capture_PaymentCapturedイベントが積まれる() {
        Payment payment = pendingPayment();

        payment.capture(Instant.now());

        assertTrue(payment.pullEvents().stream().anyMatch(e -> e instanceof com.example.recordshop.domain.payment.event.PaymentCaptured));
    }
}
