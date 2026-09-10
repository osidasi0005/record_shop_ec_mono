package com.example.recordshop.domain.customer;

import com.example.recordshop.domain.shared.InvariantViolationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailVerificationTest {

    private static final Duration TTL = Duration.ofMinutes(10);

    @Test
    void issue_発行された確認コードは6桁の数字である() {
        EmailVerification verification = EmailVerification.issue(EmailVerificationId.generate(),
                new Email("taro@example.com"), "hashed-password", "山田太郎", Instant.now(), TTL);

        assertTrue(verification.verificationCode().matches("[0-9]{6}"));
    }

    @Test
    void issue_有効期限はnowにttlを加えた時刻になる() {
        Instant now = Instant.parse("2026-08-15T00:00:00Z");

        EmailVerification verification = EmailVerification.issue(EmailVerificationId.generate(),
                new Email("taro@example.com"), "hashed-password", "山田太郎", now, TTL);

        assertEquals(now.plus(TTL), verification.expiresAt());
    }

    @Test
    void confirm_正しいコードなら例外を投げない() {
        Instant now = Instant.now();
        EmailVerification verification = EmailVerification.issue(EmailVerificationId.generate(),
                new Email("taro@example.com"), "hashed-password", "山田太郎", now, TTL);

        verification.confirm(verification.verificationCode(), now);
    }

    @Test
    void confirm_誤ったコードは例外を投げattemptCountが増える() {
        Instant now = Instant.now();
        EmailVerification verification = EmailVerification.issue(EmailVerificationId.generate(),
                new Email("taro@example.com"), "hashed-password", "山田太郎", now, TTL);

        assertThrows(InvariantViolationException.class, () -> verification.confirm("000000".equals(
                verification.verificationCode()) ? "111111" : "000000", now));
        assertEquals(1, verification.attemptCount());
    }

    @Test
    void confirm_試行回数が上限に達すると正しいコードでも例外を投げる() {
        Instant now = Instant.now();
        EmailVerification verification = EmailVerification.issue(EmailVerificationId.generate(),
                new Email("taro@example.com"), "hashed-password", "山田太郎", now, TTL);
        String wrongCode = "000000".equals(verification.verificationCode()) ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            assertThrows(InvariantViolationException.class, () -> verification.confirm(wrongCode, now));
        }

        assertThrows(InvariantViolationException.class,
                () -> verification.confirm(verification.verificationCode(), now));
    }

    @Test
    void confirm_有効期限切れなら正しいコードでも例外を投げる() {
        Instant now = Instant.now();
        EmailVerification verification = EmailVerification.issue(EmailVerificationId.generate(),
                new Email("taro@example.com"), "hashed-password", "山田太郎", now, TTL);

        assertThrows(InvariantViolationException.class,
                () -> verification.confirm(verification.verificationCode(), now.plus(TTL).plusSeconds(1)));
    }

    @Test
    void reissueCode_コードと有効期限と試行回数がリセットされる() {
        Instant now = Instant.now();
        EmailVerification verification = EmailVerification.issue(EmailVerificationId.generate(),
                new Email("taro@example.com"), "hashed-password", "山田太郎", now, TTL);
        String wrongCode = "000000".equals(verification.verificationCode()) ? "111111" : "000000";
        try {
            verification.confirm(wrongCode, now);
        } catch (InvariantViolationException ignored) {
            // attemptCountを1に増やしておく
        }

        Instant reissuedAt = now.plusSeconds(60);
        verification.reissueCode(reissuedAt, TTL);

        assertEquals(0, verification.attemptCount());
        assertEquals(reissuedAt.plus(TTL), verification.expiresAt());
        verification.confirm(verification.verificationCode(), reissuedAt);
    }
}
