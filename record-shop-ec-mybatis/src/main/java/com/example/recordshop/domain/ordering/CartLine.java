package com.example.recordshop.domain.ordering;

import com.example.recordshop.domain.inventory.ConditionType;
import com.example.recordshop.domain.inventory.ListingId;
import com.example.recordshop.domain.shared.InvariantViolationException;

import java.time.Instant;
import java.util.Objects;

/**
 * カート内の 1 行。Used Listing を含む行は数量が常に 1 でなければならない。
 * conditionType は Cart 自身がこの不変条件を守るために保持する(Listing の複製ではない)。
 */
public record CartLine(ListingId listingId, ConditionType conditionType, int quantity, Instant addedAt) {

    public CartLine {
        Objects.requireNonNull(listingId, "listingId must not be null");
        Objects.requireNonNull(conditionType, "conditionType must not be null");
        Objects.requireNonNull(addedAt, "addedAt must not be null");
        if (quantity < 1) {
            throw new InvariantViolationException("quantity must be >= 1: " + quantity);
        }
        if (conditionType == ConditionType.USED && quantity != 1) {
            throw new InvariantViolationException("Used Listing を含む CartLine の数量は常に 1 です: " + quantity);
        }
    }
}
