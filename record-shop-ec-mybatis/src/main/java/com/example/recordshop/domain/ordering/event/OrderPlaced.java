package com.example.recordshop.domain.ordering.event;

import com.example.recordshop.domain.ordering.OrderId;
import com.example.recordshop.domain.shared.Money;
import com.example.recordshop.domain.shared.event.DomainEvent;

import java.time.Instant;

public record OrderPlaced(OrderId orderId, Money totalAmount, Instant occurredAt) implements DomainEvent {
}
