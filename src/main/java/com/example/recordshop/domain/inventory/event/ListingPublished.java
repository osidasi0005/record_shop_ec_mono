package com.example.recordshop.domain.inventory.event;

import com.example.recordshop.domain.catalog.PressingId;
import com.example.recordshop.domain.inventory.ListingId;
import com.example.recordshop.domain.shared.event.DomainEvent;

import java.time.Instant;

public record ListingPublished(ListingId listingId, PressingId pressingId, Instant occurredAt)
        implements DomainEvent {
}
