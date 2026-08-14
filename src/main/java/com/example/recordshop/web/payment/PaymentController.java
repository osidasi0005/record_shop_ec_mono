package com.example.recordshop.web.payment;

import com.example.recordshop.domain.ordering.OrderId;
import com.example.recordshop.domain.payment.Payment;
import com.example.recordshop.domain.payment.PaymentCaptureService;
import com.example.recordshop.domain.payment.PaymentId;
import com.example.recordshop.domain.payment.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Payment コンテキストに対する最小限の REST API。
 *
 * <p>「歩く骨格」の第四歩として、決済Capture(即時成功)のみを実装している。
 * 実際の決済ゲートウェイ連携・失敗シナリオ・返金は後続フェーズで追加する。
 */
@RestController
public class PaymentController {

    private final PaymentCaptureService paymentCaptureService;
    private final PaymentRepository paymentRepository;

    public PaymentController(PaymentCaptureService paymentCaptureService, PaymentRepository paymentRepository) {
        this.paymentCaptureService = paymentCaptureService;
        this.paymentRepository = paymentRepository;
    }

    @PostMapping("/api/orders/{orderId}/payments")
    @Transactional
    public ResponseEntity<PaymentResponse> capture(@PathVariable String orderId,
                                                     @RequestBody CapturePaymentRequest request) {
        Payment payment = paymentCaptureService.capturePayment(OrderId.of(orderId), request.method(), Instant.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentResponse.from(payment));
    }

    @GetMapping("/api/payments/{paymentId}")
    public PaymentResponse findById(@PathVariable String paymentId) {
        Payment payment = paymentRepository.findById(PaymentId.of(paymentId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + paymentId));
        return PaymentResponse.from(payment);
    }
}
