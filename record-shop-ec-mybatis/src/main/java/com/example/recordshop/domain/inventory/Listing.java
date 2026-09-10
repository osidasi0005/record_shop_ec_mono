package com.example.recordshop.domain.inventory;

import com.example.recordshop.domain.catalog.PressingId;
import com.example.recordshop.domain.inventory.event.ListingPublished;
import com.example.recordshop.domain.inventory.event.ListingSold;
import com.example.recordshop.domain.shared.IllegalStateTransitionException;
import com.example.recordshop.domain.shared.InvariantViolationException;
import com.example.recordshop.domain.shared.Money;
import com.example.recordshop.domain.shared.event.DomainEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 出品(Listing)。Inventory コンテキストの集約ルート。
 * 実際に売買される 1 件を表す。New は在庫数で管理し、Used は個体そのもの(常に数量 1)を表す。
 *
 * <p><b>不変条件</b>
 * <ul>
 *   <li>Used Listing が SOLD になったら二度と PUBLISHED / RESERVED へ戻せない(不可逆)</li>
 *   <li>Used Listing の予約・売約は常に数量 1</li>
 *   <li>New Listing の在庫数は 0 未満にならない。0 になったら自動的に OUT_OF_STOCK</li>
 *   <li>状態遷移は {@link ListingStatus} で許可された経路のみ</li>
 * </ul>
 */
public final class Listing {

    private final ListingId listingId;
    private final PressingId pressingId;
    private final ConditionType conditionType;
    private Money price;
    private ListingStatus status;

    // New 専用(Used では null)
    private Integer stockQuantity;

    // Used 専用(New では null)
    private GoldmineGrade vinylGrade;
    private GoldmineGrade sleeveGrade;
    private String sellerNote;

    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    private Listing(ListingId listingId, PressingId pressingId, ConditionType conditionType, Money price) {
        this.listingId = Objects.requireNonNull(listingId, "listingId must not be null");
        this.pressingId = Objects.requireNonNull(pressingId, "pressingId must not be null");
        this.conditionType = Objects.requireNonNull(conditionType, "conditionType must not be null");
        this.price = Objects.requireNonNull(price, "price must not be null");
        this.status = ListingStatus.DRAFT;
    }

    public static Listing newCopy(ListingId listingId, PressingId pressingId, Money price, int initialStock) {
        if (initialStock < 1) {
            throw new InvariantViolationException("initialStock must be >= 1: " + initialStock);
        }
        Listing listing = new Listing(listingId, pressingId, ConditionType.NEW, price);
        listing.stockQuantity = initialStock;
        return listing;
    }

    public static Listing usedCopy(ListingId listingId, PressingId pressingId, Money price,
                                    GoldmineGrade vinylGrade, GoldmineGrade sleeveGrade, String sellerNote) {
        Listing listing = new Listing(listingId, pressingId, ConditionType.USED, price);
        listing.vinylGrade = Objects.requireNonNull(vinylGrade, "vinylGrade must not be null");
        listing.sleeveGrade = Objects.requireNonNull(sleeveGrade, "sleeveGrade must not be null");
        listing.sellerNote = sellerNote;
        return listing;
    }

    /**
     * 永続化層からの再構築用ファクトリ。DRAFT からの状態遷移を経由せず、DB保存済みの状態(status・在庫数等)を
     * そのまま復元する。リポジトリ実装(インフラ層)から呼ばれる想定。
     */
    public static Listing reconstitute(ListingId listingId, PressingId pressingId, ConditionType conditionType,
                                        Money price, ListingStatus status, Integer stockQuantity,
                                        GoldmineGrade vinylGrade, GoldmineGrade sleeveGrade, String sellerNote) {
        Listing listing = new Listing(listingId, pressingId, conditionType, price);
        listing.status = status;
        listing.stockQuantity = stockQuantity;
        listing.vinylGrade = vinylGrade;
        listing.sleeveGrade = sleeveGrade;
        listing.sellerNote = sellerNote;
        return listing;
    }

    /** DRAFT -&gt; PUBLISHED。{@link ListingPublished} イベントを積む。 */
    public void publish(Instant now) {
        transitionTo(ListingStatus.PUBLISHED);
        pendingEvents.add(new ListingPublished(listingId, pressingId, now));
    }

    /**
     * 注文確定に向けて在庫を仮押さえする。
     * Used は quantity=1 固定で PUBLISHED -&gt; RESERVED。
     * New は quantity 分だけ在庫を引き当てる(在庫が尽きたら自動的に OUT_OF_STOCK)。
     */
    public void reserve(int quantity) {
        if (quantity < 1) {
            throw new InvariantViolationException("quantity must be >= 1: " + quantity);
        }
        if (conditionType == ConditionType.USED) {
            if (quantity != 1) {
                throw new InvariantViolationException("Used Listing の数量は常に 1 です: " + quantity);
            }
            transitionTo(ListingStatus.RESERVED);
        } else {
            requireStatus(ListingStatus.PUBLISHED, "在庫を予約");
            if (quantity > stockQuantity) {
                throw new InvariantViolationException(
                        "在庫不足です: 要求=%d, 在庫=%d".formatted(quantity, stockQuantity));
            }
            stockQuantity -= quantity;
            if (stockQuantity == 0) {
                transitionTo(ListingStatus.OUT_OF_STOCK);
            }
        }
    }

    /**
     * 決済失敗・注文キャンセルなどで予約を解放する(発送前のみ)。
     * Used は RESERVED -&gt; PUBLISHED。New は在庫を quantity 分だけ戻す。
     */
    public void cancelReservation(int quantity) {
        if (quantity < 1) {
            throw new InvariantViolationException("quantity must be >= 1: " + quantity);
        }
        if (conditionType == ConditionType.USED) {
            transitionTo(ListingStatus.PUBLISHED);
        } else {
            stockQuantity += quantity;
            if (status == ListingStatus.OUT_OF_STOCK) {
                transitionTo(ListingStatus.PUBLISHED);
            }
        }
    }

    /**
     * 決済確定(Capture)を受けて販売を確定する。
     * Used は RESERVED -&gt; SOLD(不可逆、{@link ListingSold} イベントを積む)。
     * New は在庫を reserve() の時点で既に引き当て済みのため状態遷移は発生しない。
     */
    public void confirmSale(int quantity, Instant now) {
        if (conditionType == ConditionType.USED) {
            transitionTo(ListingStatus.SOLD);
            pendingEvents.add(new ListingSold(listingId, now));
        } else {
            if (status == ListingStatus.REMOVED) {
                throw new IllegalStateTransitionException("REMOVED な Listing の販売は確定できません");
            }
            // New は reserve() 時点で在庫を引き当て済みなのでステータス遷移は不要。
        }
    }

    private void requireStatus(ListingStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateTransitionException(
                    "%s は Status=%s のときのみ可能です(現在: %s)".formatted(action, expected, status));
        }
    }

    private void transitionTo(ListingStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateTransitionException(
                    "Listing のステータスを %s から %s へ変更することはできません".formatted(status, next));
        }
        status = next;
    }

    public List<DomainEvent> pullEvents() {
        List<DomainEvent> events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }

    public ListingId listingId() {
        return listingId;
    }

    public PressingId pressingId() {
        return pressingId;
    }

    public ConditionType conditionType() {
        return conditionType;
    }

    public Money price() {
        return price;
    }

    public ListingStatus status() {
        return status;
    }

    public Integer stockQuantity() {
        return stockQuantity;
    }

    public GoldmineGrade vinylGrade() {
        return vinylGrade;
    }

    public GoldmineGrade sleeveGrade() {
        return sleeveGrade;
    }

    public String sellerNote() {
        return sellerNote;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Listing other)) return false;
        return listingId.equals(other.listingId);
    }

    @Override
    public int hashCode() {
        return listingId.hashCode();
    }

    @Override
    public String toString() {
        return "Listing{%s, %s, %s, %s}".formatted(listingId, conditionType, status, price);
    }
}
