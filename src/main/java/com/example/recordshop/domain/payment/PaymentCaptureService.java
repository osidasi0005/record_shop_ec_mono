package com.example.recordshop.domain.payment;

import com.example.recordshop.domain.ordering.Order;
import com.example.recordshop.domain.ordering.OrderId;
import com.example.recordshop.domain.ordering.OrderRepository;
import com.example.recordshop.domain.shared.InvariantViolationException;

import java.time.Instant;

/**
 * 決済確定のドメインサービス。
 *
 * <p>Order と Payment は別々の集約であり、それぞれが自分の不変条件しか守れない。
 * 「Order の合計金額と一致する Payment を起票し、即時Captureし、Order を PAID にする」という
 * 複数集約にまたがる一連の手続きは、{@link com.example.recordshop.domain.ordering.OrderPlacementService}
 * と同様にこの domain service が調停する。
 */
public final class PaymentCaptureService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    public PaymentCaptureService(OrderRepository orderRepository, PaymentRepository paymentRepository) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
    }

    public Payment capturePayment(OrderId orderId, PaymentMethod method, Instant now) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new InvariantViolationException("Order が見つかりません: " + orderId));

        Payment payment = Payment.initiate(PaymentId.generate(), orderId, order.totalAmount(), method);
        payment.capture(now);
        order.markPaid();

        paymentRepository.save(payment);
        orderRepository.save(order);

        return payment;
    }
}
