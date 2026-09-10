package com.example.recordshop.domain.ordering;

import com.example.recordshop.domain.inventory.ListingId;
import com.example.recordshop.domain.shared.InvariantViolationException;
import com.example.recordshop.domain.shared.Money;

import java.util.Objects;

/**
 * 注文明細。1 行が 1 Listing に対応する。
 * pressingSnapshot は確定時点で複製された不変のコピーであり、以後カタログが変わっても追従しない。
 */
public record OrderLine(ListingId listingId, PressingSnapshot pressingSnapshot, Money unitPrice, int quantity) {

    public OrderLine {
        Objects.requireNonNull(listingId, "listingId must not be null");
        Objects.requireNonNull(pressingSnapshot, "pressingSnapshot must not be null");
        Objects.requireNonNull(unitPrice, "unitPrice must not be null");
        if (quantity < 1) {
            throw new InvariantViolationException("quantity must be >= 1: " + quantity);
        }
    }

    public Money lineTotal() {
        return unitPrice.multiply(quantity);
    }
}
