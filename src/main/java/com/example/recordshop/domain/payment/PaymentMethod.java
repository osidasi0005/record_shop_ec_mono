package com.example.recordshop.domain.payment;

/**
 * 決済手段。実際の決済ゲートウェイ連携(Stripe等)は歩く骨格フェーズでは行わず、
 * 「即時Capture成功する」ものとして扱う(決済失敗のシミュレーションは {@link Payment#fail} で表現)。
 */
public enum PaymentMethod {
    CREDIT_CARD,
    BANK_TRANSFER
}
