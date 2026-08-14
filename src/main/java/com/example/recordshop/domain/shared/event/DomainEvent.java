package com.example.recordshop.domain.shared.event;

import java.time.Instant;

/**
 * 全ドメインイベントのマーカーインターフェース。
 *
 * <p>集約は状態変更のたびにイベントを {@code pendingEvents} に積み、
 * アプリケーション層がトランザクション後に {@code pullEvents()} で取り出して発行する想定。
 */
public interface DomainEvent {
    Instant occurredAt();
}
