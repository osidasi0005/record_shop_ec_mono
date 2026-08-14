package com.example.recordshop.domain.ordering;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 注文ステータス。許可された遷移のみを受け付ける状態機械として振る舞う。
 *
 * <pre>
 * PENDING -&gt; PAID -&gt; SHIPPED -&gt; DELIVERED
 *    \-&gt; CANCELLED     \-&gt; CANCELLED(決済済みキャンセル=返金扱い)
 * </pre>
 *
 * <p>ShippingAddress の変更可否は状態遷移そのものではなく、現在の Status が
 * {@link #allowsShippingAddressChange()} を満たすかどうかで判定する({@link Order#changeShippingAddress}).
 */
public enum OrderStatus {
    PENDING,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(PENDING, EnumSet.of(PAID, CANCELLED));
        ALLOWED_TRANSITIONS.put(PAID, EnumSet.of(SHIPPED, CANCELLED));
        ALLOWED_TRANSITIONS.put(SHIPPED, EnumSet.of(DELIVERED));
        ALLOWED_TRANSITIONS.put(DELIVERED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    public boolean canTransitionTo(OrderStatus next) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, EnumSet.noneOf(OrderStatus.class)).contains(next);
    }

    /** 発送(Shipment作成)より前かどうか。ShippingAddress はこの間だけ変更を許可する。 */
    public boolean allowsShippingAddressChange() {
        return this == PENDING || this == PAID;
    }

    /** 決済確定(Capture)より前かどうか。BillingAddress はこの間だけ変更を許可する。 */
    public boolean allowsBillingAddressChange() {
        return this == PENDING;
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }
}
