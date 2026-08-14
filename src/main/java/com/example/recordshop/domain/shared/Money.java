package com.example.recordshop.domain.shared;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * 金額を表す値オブジェクト。
 *
 * <p>価格・合計金額のいずれも「値が付いている」ことが前提のため、負の金額は許容しない。
 * 0円(無料の特典など)は許容する。
 */
public record Money(BigDecimal amount, Currency currency) {

    public static final Currency JPY = Currency.getInstance("JPY");

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative: " + amount);
        }
    }

    public static Money jpy(long amount) {
        return new Money(BigDecimal.valueOf(amount), JPY);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money multiply(int factor) {
        if (factor < 0) {
            throw new IllegalArgumentException("factor must not be negative: " + factor);
        }
        return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other must not be null");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "currency mismatch: %s vs %s".formatted(currency, other.currency));
        }
    }

    @Override
    public String toString() {
        return currency.getCurrencyCode() + " " + amount.toPlainString();
    }
}
