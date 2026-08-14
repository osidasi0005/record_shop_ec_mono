package com.example.recordshop.domain.inventory.event;

import com.example.recordshop.domain.inventory.ListingId;
import com.example.recordshop.domain.shared.event.DomainEvent;

import java.time.Instant;

/**
 * Used Listing が売約済みになったことを表すイベント。SOLD は不可逆な終端状態。
 */
public record ListingSold(ListingId listingId, Instant occurredAt) implements DomainEvent {
}
