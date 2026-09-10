package com.example.recordshop.domain.inventory;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Listing のステータス。許可された遷移のみを受け付ける状態機械として振る舞う。
 *
 * <pre>
 * DRAFT -&gt; PUBLISHED -&gt; RESERVED -&gt; SOLD            (Used の主経路。SOLD は終端で不可逆)
 *                    \-&gt; OUT_OF_STOCK -&gt; PUBLISHED   (New の在庫切れ/再入荷)
 *          いずれの状態からも -&gt; REMOVED(出品取り下げ)
 * </pre>
 */
public enum ListingStatus {
    DRAFT,
    PUBLISHED,
    RESERVED,
    SOLD,
    OUT_OF_STOCK,
    REMOVED;

    private static final Map<ListingStatus, Set<ListingStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(ListingStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(DRAFT, EnumSet.of(PUBLISHED, REMOVED));
        ALLOWED_TRANSITIONS.put(PUBLISHED, EnumSet.of(RESERVED, OUT_OF_STOCK, REMOVED));
        ALLOWED_TRANSITIONS.put(RESERVED, EnumSet.of(PUBLISHED, SOLD));
        ALLOWED_TRANSITIONS.put(OUT_OF_STOCK, EnumSet.of(PUBLISHED, REMOVED));
        ALLOWED_TRANSITIONS.put(SOLD, EnumSet.noneOf(ListingStatus.class));
        ALLOWED_TRANSITIONS.put(REMOVED, EnumSet.noneOf(ListingStatus.class));
    }

    public boolean canTransitionTo(ListingStatus next) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, EnumSet.noneOf(ListingStatus.class)).contains(next);
    }

    public boolean isTerminal() {
        return this == SOLD || this == REMOVED;
    }
}
