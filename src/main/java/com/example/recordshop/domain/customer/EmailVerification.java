package com.example.recordshop.domain.customer;

import com.example.recordshop.domain.shared.InvariantViolationException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 会員登録の仮登録(メールアドレス確認待ち)。Customer コンテキストの、Customer とは
 * 別の集約ルート({@link com.example.recordshop.domain.ordering}パッケージが Cart と Order を
 * 同居させているのと同じく、1パッケージに複数集約が同居する構成)。
 *
 * <p>確認コードの照合に成功して初めて {@link CustomerRegistrationService} 経由で
 * Customer 集約が作られる。それまでは平文パスワードを保持しない(ハッシュ化済みの
 * 状態で保持する)以外、Customer 集約には一切影響を与えない。
 *
 * <p><b>不変条件</b>
 * <ul>
 *   <li>確認コードは有効期限内、かつ試行回数上限に達していない場合のみ照合できる</li>
 * </ul>
 */
public final class EmailVerification {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS = 5;

    private final EmailVerificationId emailVerificationId;
    private final Email email;
    private final String passwordHash;
    private final String displayName;
    private String verificationCode;
    private Instant expiresAt;
    private int attemptCount;

    private EmailVerification(EmailVerificationId emailVerificationId, Email email, String passwordHash,
                               String displayName, String verificationCode, Instant expiresAt, int attemptCount) {
        this.emailVerificationId = Objects.requireNonNull(emailVerificationId, "emailVerificationId must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.passwordHash = requireNonBlank(passwordHash, "passwordHash");
        this.displayName = requireNonBlank(displayName, "displayName");
        this.verificationCode = requireNonBlank(verificationCode, "verificationCode");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.attemptCount = attemptCount;
    }

    /** 新規に確認コードを発行する。 */
    public static EmailVerification issue(EmailVerificationId emailVerificationId, Email email, String passwordHash,
                                           String displayName, Instant now, Duration ttl) {
        return new EmailVerification(emailVerificationId, email, passwordHash, displayName,
                generateCode(), now.plus(ttl), 0);
    }

    /** 永続化層からの再構築用ファクトリ。リポジトリ実装(インフラ層)から呼ばれる想定。 */
    public static EmailVerification reconstitute(EmailVerificationId emailVerificationId, Email email,
            String passwordHash, String displayName, String verificationCode, Instant expiresAt, int attemptCount) {
        return new EmailVerification(emailVerificationId, email, passwordHash, displayName,
                verificationCode, expiresAt, attemptCount);
    }

    /** 同じメールアドレスでの再登録要求時に、確認コード・有効期限・試行回数をリセットする。 */
    public void reissueCode(Instant now, Duration ttl) {
        this.verificationCode = generateCode();
        this.expiresAt = now.plus(ttl);
        this.attemptCount = 0;
    }

    /**
     * 入力された確認コードを照合する。正しければ何もしない。
     * 誤り・期限切れ・試行回数上限のいずれかに該当する場合は {@link InvariantViolationException} を投げる。
     *
     * <p>コード不一致時は attemptCount をインクリメントしてから例外を投げるため、
     * 呼び出し側(ドメインサービス)は catch 後に必ずこのインスタンスを保存し直す必要がある。
     */
    public void confirm(String inputCode, Instant now) {
        if (now.isAfter(expiresAt)) {
            throw new InvariantViolationException("確認コードの有効期限が切れています。もう一度会員登録をやり直してください");
        }
        if (attemptCount >= MAX_ATTEMPTS) {
            throw new InvariantViolationException("確認コードの入力回数が上限に達しました。もう一度会員登録をやり直してください");
        }
        if (!verificationCode.equals(inputCode)) {
            attemptCount++;
            throw new InvariantViolationException("確認コードが正しくありません");
        }
    }

    /** SecureRandomでの6桁ゼロ埋め数字文字列の生成を、生成ロジックを持つ集約自身に閉じ込める。 */
    private static String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public EmailVerificationId emailVerificationId() {
        return emailVerificationId;
    }

    public Email email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String displayName() {
        return displayName;
    }

    public String verificationCode() {
        return verificationCode;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public int attemptCount() {
        return attemptCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmailVerification other)) return false;
        return emailVerificationId.equals(other.emailVerificationId);
    }

    @Override
    public int hashCode() {
        return emailVerificationId.hashCode();
    }

    @Override
    public String toString() {
        return "EmailVerification{%s, %s}".formatted(emailVerificationId, email);
    }
}
