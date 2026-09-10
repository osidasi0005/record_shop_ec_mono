package com.example.recordshop.domain.payment.event;

import com.example.recordshop.domain.ordering.OrderId;
import com.example.recordshop.domain.payment.PaymentId;
import com.example.recordshop.domain.shared.Money;
import com.example.recordshop.domain.shared.event.DomainEvent;

import java.time.Instant;

public record PaymentCaptured(PaymentId paymentId, OrderId orderId, Money amount, Instant occurredAt)
        implements DomainEvent {
}
