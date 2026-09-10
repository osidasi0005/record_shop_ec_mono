package com.example.recordshop.domain.customer;

import java.util.Optional;

/**
 * EmailVerification 集約の永続化ポート(インターフェースのみ)。
 *
 * <p>email には UNIQUE 制約があり、同一メールアドレスの仮登録は常に1件のみ存在する。
 * {@link #save} は既存の仮登録があれば上書きする(upsert)。
 */
public interface EmailVerificationRepository {

    /** 同一メールアドレスの既存レコードがあれば上書きする(upsert)。 */
    void save(EmailVerification emailVerification);

    Optional<EmailVerification> findByEmail(Email email);

    void deleteByEmail(Email email);
}
