package com.example.recordshop.infrastructure.mybatis;

import com.example.recordshop.domain.customer.Email;
import com.example.recordshop.domain.customer.EmailVerification;
import com.example.recordshop.domain.customer.EmailVerificationId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class MyBatisEmailVerificationRepositoryTest {

    private static final Duration TTL = Duration.ofMinutes(10);

    @Autowired
    private MyBatisEmailVerificationRepository repository;

    @Test
    void save_findByEmail_で保存した仮登録が同じ内容で復元できる() {
        Instant now = Instant.now();
        EmailVerification verification = EmailVerification.issue(EmailVerificationId.generate(),
                new Email("taro@example.com"), "hashed-password", "山田太郎", now, TTL);

        repository.save(verification);
        EmailVerification found = repository.findByEmail(new Email("taro@example.com")).orElseThrow();

        assertThat(found.email()).isEqualTo(new Email("taro@example.com"));
        assertThat(found.displayName()).isEqualTo("山田太郎");
        assertThat(found.verificationCode()).isEqualTo(verification.verificationCode());
    }

    @Test
    void save_同じEmailで2回保存すると上書きされる() {
        Instant now = Instant.now();
        EmailVerification first = EmailVerification.issue(EmailVerificationId.generate(),
                new Email("taro@example.com"), "hashed-password", "山田太郎", now, TTL);
        repository.save(first);

        EmailVerification second = EmailVerification.issue(EmailVerificationId.generate(),
                new Email("taro@example.com"), "hashed-password2", "山田次郎", now, TTL);
        repository.save(second);

        EmailVerification found = repository.findByEmail(new Email("taro@example.com")).orElseThrow();
        assertThat(found.displayName()).isEqualTo("山田次郎");
        assertThat(found.verificationCode()).isEqualTo(second.verificationCode());
    }

    @Test
    void deleteByEmail_削除後はfindByEmailが空になる() {
        EmailVerification verification = EmailVerification.issue(EmailVerificationId.generate(),
                new Email("taro@example.com"), "hashed-password", "山田太郎", Instant.now(), TTL);
        repository.save(verification);

        repository.deleteByEmail(new Email("taro@example.com"));

        assertThat(repository.findByEmail(new Email("taro@example.com"))).isEmpty();
    }
}
