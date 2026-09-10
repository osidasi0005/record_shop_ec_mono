package com.example.recordshop.domain.payment;

import com.example.recordshop.domain.ordering.OrderId;
import com.example.recordshop.domain.payment.event.PaymentCaptured;
import com.example.recordshop.domain.payment.event.PaymentRefunded;
import com.example.recordshop.domain.shared.IllegalStateTransitionException;
import com.example.recordshop.domain.shared.Money;
import com.example.recordshop.domain.shared.event.DomainEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 決済(Payment)。Payment コンテキストの集約ルート。
 *
 * <p>{@code orderId} は Ordering コンテキストの {@link OrderId} への参照のみを保持し(別集約なのでCASCADEなし)、
 * 「歩く骨格」フェーズでは実際の決済ゲートウェイ連携は行わず、Capture は常に即時成功として扱う
 * (決済失敗は {@link #fail} で明示的にシミュレートする)。
 *
 * <p><b>不変条件</b>
 * <ul>
 *   <li>ステータス遷移は {@link PaymentStatus} で許可された経路のみ(PENDING→CAPTURED→REFUNDED、PENDING→FAILED)</li>
 *   <li>CAPTURED になったら {@code capturedAt} が確定し、以後変わらない</li>
 * </ul>
 */
public final class Payment {

    private final PaymentId paymentId;
    private final OrderId orderId;
    private final Money amount;
    private final PaymentMethod method;
    private PaymentStatus status;
    private Instant capturedAt;

    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    private Payment(PaymentId paymentId, OrderId orderId, Money amount, PaymentMethod method) {
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId must not be null");
        this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.method = Objects.requireNonNull(method, "method must not be null");
        this.status = PaymentStatus.PENDING;
    }

    /** 決済を PENDING 状態で起票する。実際の Capture は {@link #capture} で行う。 */
    public static Payment initiate(PaymentId paymentId, OrderId orderId, Money amount, PaymentMethod method) {
        return new Payment(paymentId, orderId, amount, method);
    }

    /**
     * 永続化層からの再構築用ファクトリ。{@link #initiate} を経由せず、DB保存済みの Status・capturedAt を
     * そのまま復元する。リポジトリ実装(インフラ層)から呼ばれる想定。
     */
    public static Payment reconstitute(PaymentId paymentId, OrderId orderId, Money amount, PaymentMethod method,
                                        PaymentStatus status, Instant capturedAt) {
        Payment payment = new Payment(paymentId, orderId, amount, method);
        payment.status = status;
        payment.capturedAt = capturedAt;
        return payment;
    }

    /** PENDING -&gt; CAPTURED。{@link PaymentCaptured} イベントを積む。 */
    public void capture(Instant now) {
        transitionTo(PaymentStatus.CAPTURED);
        this.capturedAt = now;
        pendingEvents.add(new PaymentCaptured(paymentId, orderId, amount, now));
    }

    /** PENDING -&gt; FAILED(不可逆)。 */
    public void fail() {
        transitionTo(PaymentStatus.FAILED);
    }

    /** CAPTURED -&gt; REFUNDED。{@link PaymentRefunded} イベントを積む。 */
    public void refund(Instant now) {
        transitionTo(PaymentStatus.REFUNDED);
        pendingEvents.add(new PaymentRefunded(paymentId, now));
    }

    private void transitionTo(PaymentStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateTransitionException(
                    "Payment のステータスを %s から %s へ変更することはできません".formatted(status, next));
        }
        status = next;
    }

    public List<DomainEvent> pullEvents() {
        List<DomainEvent> events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }

    public PaymentId paymentId() {
        return paymentId;
    }

    public OrderId orderId() {
        return orderId;
    }

    public Money amount() {
        return amount;
    }

    public PaymentMethod method() {
        return method;
    }

    public PaymentStatus status() {
        return status;
    }

    public Optional<Instant> capturedAt() {
        return Optional.ofNullable(capturedAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Payment other)) return false;
        return paymentId.equals(other.paymentId);
    }

    @Override
    public int hashCode() {
        return paymentId.hashCode();
    }

    @Override
    public String toString() {
        return "Payment{%s, order=%s, %s, %s}".formatted(paymentId, orderId, status, amount);
    }
}
