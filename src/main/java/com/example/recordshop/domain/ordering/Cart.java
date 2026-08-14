package com.example.recordshop.domain.ordering;

import com.example.recordshop.domain.customer.CustomerId;
import com.example.recordshop.domain.inventory.ConditionType;
import com.example.recordshop.domain.inventory.ListingId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * カート。Ordering コンテキストの集約ルート。
 *
 * <p>カート追加の時点では在庫を予約しない。排他確認は注文確定({@link OrderPlacementService})で行う。
 */
public final class Cart {

    private final CartId cartId;
    private final CustomerId customerId;
    private final List<CartLine> lines = new ArrayList<>();

    private Cart(CartId cartId, CustomerId customerId) {
        this.cartId = Objects.requireNonNull(cartId, "cartId must not be null");
        this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
    }

    public static Cart open(CartId cartId, CustomerId customerId) {
        return new Cart(cartId, customerId);
    }

    public void addLine(ListingId listingId, ConditionType conditionType, int quantity, Instant now) {
        lines.removeIf(line -> line.listingId().equals(listingId));
        lines.add(new CartLine(listingId, conditionType, quantity, now));
    }

    public void removeLine(ListingId listingId) {
        lines.removeIf(line -> line.listingId().equals(listingId));
    }

    public List<CartLine> lines() {
        return Collections.unmodifiableList(lines);
    }

    public CartId cartId() {
        return cartId;
    }

    public CustomerId customerId() {
        return customerId;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }
}
