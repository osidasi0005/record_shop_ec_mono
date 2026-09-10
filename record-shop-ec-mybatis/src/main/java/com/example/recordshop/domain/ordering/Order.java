package com.example.recordshop.domain.ordering;

import com.example.recordshop.domain.customer.CustomerId;
import com.example.recordshop.domain.ordering.event.OrderPlaced;
import com.example.recordshop.domain.ordering.event.ShippingAddressChanged;
import com.example.recordshop.domain.shared.Address;
import com.example.recordshop.domain.shared.IllegalStateTransitionException;
import com.example.recordshop.domain.shared.InvariantViolationException;
import com.example.recordshop.domain.shared.Money;
import com.example.recordshop.domain.shared.event.DomainEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 注文(Order)。Ordering コンテキストの集約ルート。
 *
 * <p><b>不変条件</b>
 * <ul>
 *   <li>OrderLine は確定時点の Pressing 情報をイミュータブルなスナップショットとして保持する</li>
 *   <li>ShippingAddress は Status が PENDING/PAID の間のみ変更できる(発送後は Fulfillment 側の関心事)</li>
 *   <li>BillingAddress は Status が PENDING の間のみ変更できる(決済確定後は実質凍結)</li>
 *   <li>ステータス遷移は {@link OrderStatus} で許可された経路のみ</li>
 * </ul>
 */
public final class Order {

    private final OrderId orderId;
    private final CustomerId customerId;
    private final List<OrderLine> lines;
    private Address shippingAddress;
    private Address billingAddress;
    private OrderStatus status;
    private final Instant placedAt;

    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    private Order(OrderId orderId, CustomerId customerId, List<OrderLine> lines,
                  Address shippingAddress, Address billingAddress, Instant placedAt) {
        this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
        this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
        if (lines == null || lines.isEmpty()) {
            throw new InvariantViolationException("注文には最低 1 件の OrderLine が必要です");
        }
        this.lines = List.copyOf(lines);
        this.shippingAddress = Objects.requireNonNull(shippingAddress, "shippingAddress must not be null");
        this.billingAddress = Objects.requireNonNull(billingAddress, "billingAddress must not be null");
        this.placedAt = Objects.requireNonNull(placedAt, "placedAt must not be null");
        this.status = OrderStatus.PENDING;
    }

    /** 注文を確定する。PENDING 状態で生成し、{@link OrderPlaced} イベントを積む。 */
    public static Order place(OrderId orderId, CustomerId customerId, List<OrderLine> lines,
                               Address shippingAddress, Address billingAddress, Instant placedAt) {
        Order order = new Order(orderId, customerId, lines, shippingAddress, billingAddress, placedAt);
        order.pendingEvents.add(new OrderPlaced(orderId, order.totalAmount(), placedAt));
        return order;
    }

    /**
     * 永続化層からの再構築用ファクトリ。{@link #place} の PENDING 固定・イベント発行を経由せず、
     * DB保存済みの Status をそのまま復元する。リポジトリ実装(インフラ層)から呼ばれる想定。
     */
    public static Order reconstitute(OrderId orderId, CustomerId customerId, List<OrderLine> lines,
                                      Address shippingAddress, Address billingAddress, OrderStatus status,
                                      Instant placedAt) {
        Order order = new Order(orderId, customerId, lines, shippingAddress, billingAddress, placedAt);
        order.status = status;
        return order;
    }

    public Money totalAmount() {
        Money total = lines.get(0).lineTotal();
        for (int i = 1; i < lines.size(); i++) {
            total = total.add(lines.get(i).lineTotal());
        }
        return total;
    }

    /**
     * 配送先を変更する。Status が PENDING/PAID の間のみ許可。
     * 発送(Shipment 作成)後の宛先変更は Fulfillment コンテキストの転送依頼として扱う。
     */
    public void changeShippingAddress(Address newAddress, Instant now) {
        if (!status.allowsShippingAddressChange()) {
            throw new IllegalStateTransitionException(
                    "Status=%s の注文は配送先を変更できません(発送準備前のみ変更可)".formatted(status));
        }
        this.shippingAddress = Objects.requireNonNull(newAddress, "newAddress must not be null");
        pendingEvents.add(new ShippingAddressChanged(orderId, now));
    }

    /**
     * 請求先を変更する。Status が PENDING の間のみ許可(決済確定後は実質凍結)。
     */
    public void changeBillingAddress(Address newAddress) {
        if (!status.allowsBillingAddressChange()) {
            throw new IllegalStateTransitionException(
                    "Status=%s の注文は請求先を変更できません(決済確定前のみ変更可)".formatted(status));
        }
        this.billingAddress = Objects.requireNonNull(newAddress, "newAddress must not be null");
    }

    public void markPaid() {
        transitionTo(OrderStatus.PAID);
    }

    public void markShipped() {
        transitionTo(OrderStatus.SHIPPED);
    }

    public void markDelivered() {
        transitionTo(OrderStatus.DELIVERED);
    }

    public void cancel() {
        transitionTo(OrderStatus.CANCELLED);
    }

    private void transitionTo(OrderStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateTransitionException(
                    "Order のステータスを %s から %s へ変更することはできません".formatted(status, next));
        }
        status = next;
    }

    public List<DomainEvent> pullEvents() {
        List<DomainEvent> events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }

    public OrderId orderId() {
        return orderId;
    }

    public CustomerId customerId() {
        return customerId;
    }

    public List<OrderLine> lines() {
        return Collections.unmodifiableList(lines);
    }

    public Address shippingAddress() {
        return shippingAddress;
    }

    public Address billingAddress() {
        return billingAddress;
    }

    public OrderStatus status() {
        return status;
    }

    public Instant placedAt() {
        return placedAt;
    }

    @Override
    public String toString() {
        return "Order{%s, status=%s, total=%s}".formatted(orderId, status, totalAmount());
    }
}
