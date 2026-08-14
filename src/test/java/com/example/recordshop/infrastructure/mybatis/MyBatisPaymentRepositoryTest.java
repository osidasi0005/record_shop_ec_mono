package com.example.recordshop.infrastructure.mybatis;

import com.example.recordshop.domain.ordering.OrderId;
import com.example.recordshop.domain.payment.Payment;
import com.example.recordshop.domain.payment.PaymentId;
import com.example.recordshop.domain.payment.PaymentMethod;
import com.example.recordshop.domain.payment.PaymentStatus;
import com.example.recordshop.domain.shared.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class MyBatisPaymentRepositoryTest {

    @Autowired
    private MyBatisPaymentRepository repository;

    @Test
    void save_findById_でCaptureされたPaymentがcapturedAtまで含めて復元できる() {
        OrderId orderId = OrderId.generate();
        Payment payment = Payment.initiate(PaymentId.generate(), orderId, Money.jpy(4200), PaymentMethod.CREDIT_CARD);
        Instant capturedAt = Instant.now();
        payment.capture(capturedAt);

        repository.save(payment);
        Payment found = repository.findById(payment.paymentId()).orElseThrow();

        assertThat(found.status()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(found.orderId()).isEqualTo(orderId);
        assertThat(found.capturedAt()).isPresent();
    }

    @Test
    void save_findById_でPENDINGのPaymentはcapturedAtが空で復元される() {
        Payment payment = Payment.initiate(PaymentId.generate(), OrderId.generate(), Money.jpy(4200), PaymentMethod.BANK_TRANSFER);

        repository.save(payment);
        Payment found = repository.findById(payment.paymentId()).orElseThrow();

        assertThat(found.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(found.capturedAt()).isEmpty();
    }
}
