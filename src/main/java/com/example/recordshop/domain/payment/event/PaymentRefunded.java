package com.example.recordshop.domain.payment.event;

import com.example.recordshop.domain.payment.PaymentId;
import com.example.recordshop.domain.shared.event.DomainEvent;

import java.time.Instant;

public record PaymentRefunded(PaymentId paymentId, Instant occurredAt) implements DomainEvent {
}
