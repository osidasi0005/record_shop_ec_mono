package com.example.recordshop.domain.ordering.event;

import com.example.recordshop.domain.ordering.OrderId;
import com.example.recordshop.domain.shared.event.DomainEvent;

import java.time.Instant;

public record ShippingAddressChanged(OrderId orderId, Instant occurredAt) implements DomainEvent {
}
