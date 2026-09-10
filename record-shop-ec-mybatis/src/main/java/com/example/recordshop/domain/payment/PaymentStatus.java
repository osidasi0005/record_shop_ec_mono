package com.example.recordshop.domain.payment;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 決済ステータス。許可された遷移のみを受け付ける状態機械として振る舞う。
 *
 * <pre>
 * PENDING -&gt; CAPTURED -&gt; REFUNDED
 *    \-&gt; FAILED
 * </pre>
 */
public enum PaymentStatus {
    PENDING,
    CAPTURED,
    FAILED,
    REFUNDED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(PaymentStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(PENDING, EnumSet.of(CAPTURED, FAILED));
        ALLOWED_TRANSITIONS.put(CAPTURED, EnumSet.of(REFUNDED));
        ALLOWED_TRANSITIONS.put(FAILED, EnumSet.noneOf(PaymentStatus.class));
        ALLOWED_TRANSITIONS.put(REFUNDED, EnumSet.noneOf(PaymentStatus.class));
    }

    public boolean canTransitionTo(PaymentStatus next) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, EnumSet.noneOf(PaymentStatus.class)).contains(next);
    }

    public boolean isTerminal() {
        return this == FAILED || this == REFUNDED;
    }
}
