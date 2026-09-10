package com.example.recordshop.infrastructure.mybatis;

import com.example.recordshop.domain.customer.Email;
import com.example.recordshop.domain.customer.EmailVerification;
import com.example.recordshop.domain.customer.EmailVerificationId;
import com.example.recordshop.domain.customer.EmailVerificationRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * {@link EmailVerificationRepository}(ドメイン層のポート)のMyBatisアダプタ実装。
 * emailの一意性はDB側のUNIQUE制約でも保証される。{@code save}はON CONFLICTが使えない環境
 * (H2のPostgreSQL互換モード等)でも動くよう、{@link CustomerMapper#countByEmail}と同じ
 * 「事前カウントでinsert/updateを分岐する」方式でupsertを実現する。
 */
@Repository
public class MyBatisEmailVerificationRepository implements EmailVerificationRepository {

    private final EmailVerificationMapper mapper;

    public MyBatisEmailVerificationRepository(EmailVerificationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void save(EmailVerification emailVerification) {
        EmailVerificationRow row = new EmailVerificationRow(
                emailVerification.emailVerificationId().value(), emailVerification.email().value(),
                emailVerification.passwordHash(), emailVerification.displayName(),
                emailVerification.verificationCode(), emailVerification.expiresAt(), emailVerification.attemptCount()
        );
        if (mapper.countByEmail(row.email()) > 0) {
            mapper.updateByEmail(row);
        } else {
            mapper.insert(row);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmailVerification> findByEmail(Email email) {
        EmailVerificationRow row = mapper.selectByEmail(email.value());
        return Optional.ofNullable(row).map(this::toDomain);
    }

    @Override
    @Transactional
    public void deleteByEmail(Email email) {
        mapper.deleteByEmail(email.value());
    }

    private EmailVerification toDomain(EmailVerificationRow row) {
        return EmailVerification.reconstitute(
                new EmailVerificationId(row.id()), new Email(row.email()), row.passwordHash(), row.displayName(),
                row.verificationCode(), row.expiresAt(), row.attemptCount()
        );
    }
}
