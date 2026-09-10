package com.example.recordshop.domain.customer;

import com.example.recordshop.domain.shared.InvariantViolationException;
import com.example.recordshop.infrastructure.memory.InMemoryCustomerRepository;
import com.example.recordshop.infrastructure.memory.InMemoryEmailVerificationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailVerificationServiceTest {

    /** テストでは実際のBCrypt等に依存させず、単純な変換で代用する。 */
    private static final PasswordHasher FAKE_HASHER = new PasswordHasher() {
        @Override
        public String hash(String rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String hash) {
            return hash.equals("hashed:" + rawPassword);
        }
    };

    /** 送信内容を記録するだけのフェイク実装。実際のメール送信は行わない。 */
    private static final class RecordingEmailSender implements EmailSender {
        record VerificationCodeMail(Email to, String displayName, String code) { }
        record CompletionMail(Email to, String displayName) { }

        final List<VerificationCodeMail> verificationCodeMails = new ArrayList<>();
        final List<CompletionMail> completionMails = new ArrayList<>();

        @Override
        public void sendVerificationCode(Email to, String displayName, String verificationCode) {
            verificationCodeMails.add(new VerificationCodeMail(to, displayName, verificationCode));
        }

        @Override
        public void sendRegistrationCompleted(Email to, String displayName) {
            completionMails.add(new CompletionMail(to, displayName));
        }
    }

    /** 送信が必ず失敗するフェイク実装(SESサンドボックスの未検証宛先などを模す)。 */
    private static final EmailSender FAILING_SENDER = new EmailSender() {
        @Override
        public void sendVerificationCode(Email to, String displayName, String verificationCode) {
            throw new EmailDeliveryException("メールの送信に失敗しました: " + to.value(), new RuntimeException("rejected"));
        }

        @Override
        public void sendRegistrationCompleted(Email to, String displayName) {
            throw new EmailDeliveryException("メールの送信に失敗しました: " + to.value(), new RuntimeException("rejected"));
        }
    };

    private final InMemoryCustomerRepository customerRepository = new InMemoryCustomerRepository();
    private final InMemoryEmailVerificationRepository emailVerificationRepository =
            new InMemoryEmailVerificationRepository();
    private final CustomerRegistrationService customerRegistrationService =
            new CustomerRegistrationService(customerRepository, FAKE_HASHER);
    private final RecordingEmailSender emailSender = new RecordingEmailSender();
    private final EmailVerificationService service = new EmailVerificationService(
            emailVerificationRepository, customerRegistrationService, FAKE_HASHER, emailSender);

    @Test
    void requestVerification_確認コードが発行されメールが送信される() {
        service.requestVerification(new Email("taro@example.com"), "s3cret", "山田太郎", Instant.now());

        assertEquals(1, emailSender.verificationCodeMails.size());
        EmailVerification saved = emailVerificationRepository.findByEmail(new Email("taro@example.com")).orElseThrow();
        assertEquals(saved.verificationCode(), emailSender.verificationCodeMails.get(0).code());
    }

    @Test
    void requestVerification_登録済みのメールは例外を投げる() {
        customerRegistrationService.register(new Email("taro@example.com"), "s3cret", "山田太郎", Instant.now());

        assertThrows(InvariantViolationException.class, () -> service.requestVerification(
                new Email("taro@example.com"), "other", "別の名前", Instant.now()));
    }

    @Test
    void requestVerification_同じメールで2回呼ぶと1回目のコードが無効化される() {
        Instant now = Instant.now();
        service.requestVerification(new Email("taro@example.com"), "s3cret", "山田太郎", now);
        String firstCode = emailVerificationRepository.findByEmail(new Email("taro@example.com"))
                .orElseThrow().verificationCode();

        service.requestVerification(new Email("taro@example.com"), "s3cret2", "山田太郎2", now);

        assertThrows(InvariantViolationException.class,
                () -> service.confirmRegistration(new Email("taro@example.com"), firstCode, now));
        assertTrue(customerRepository.findByEmail(new Email("taro@example.com")).isEmpty());
    }

    @Test
    void confirmRegistration_正しいコードでCustomerが作成され仮登録は削除される() {
        Instant now = Instant.now();
        service.requestVerification(new Email("taro@example.com"), "s3cret", "山田太郎", now);
        String code = emailVerificationRepository.findByEmail(new Email("taro@example.com")).orElseThrow().verificationCode();

        Customer customer = service.confirmRegistration(new Email("taro@example.com"), code, now);

        assertEquals("山田太郎", customer.displayName());
        assertTrue(customerRepository.findByEmail(new Email("taro@example.com")).isPresent());
        assertTrue(emailVerificationRepository.findByEmail(new Email("taro@example.com")).isEmpty());
        assertEquals(1, emailSender.completionMails.size());
    }

    @Test
    void confirmRegistration_誤ったコードではCustomerを作成しない() {
        Instant now = Instant.now();
        service.requestVerification(new Email("taro@example.com"), "s3cret", "山田太郎", now);
        String code = emailVerificationRepository.findByEmail(new Email("taro@example.com")).orElseThrow().verificationCode();
        String wrongCode = "000000".equals(code) ? "111111" : "000000";

        assertThrows(InvariantViolationException.class,
                () -> service.confirmRegistration(new Email("taro@example.com"), wrongCode, now));

        assertFalse(customerRepository.findByEmail(new Email("taro@example.com")).isPresent());
        assertEquals(0, emailSender.completionMails.size());
    }

    @Test
    void confirmRegistration_仮登録が存在しない場合は例外を投げる() {
        assertThrows(InvariantViolationException.class,
                () -> service.confirmRegistration(new Email("nobody@example.com"), "123456", Instant.now()));
    }

    @Test
    void requestVerification_確認コードメールの送信に失敗したら仮登録を残さない() {
        // 保存してから送信すると、利用者はコードを受け取れないのに仮登録だけ残ってしまう。
        EmailVerificationService failing = new EmailVerificationService(
                emailVerificationRepository, customerRegistrationService, FAKE_HASHER, FAILING_SENDER);

        assertThrows(EmailDeliveryException.class, () -> failing.requestVerification(
                new Email("taro@example.com"), "s3cret", "山田太郎", Instant.now()));

        assertTrue(emailVerificationRepository.findByEmail(new Email("taro@example.com")).isEmpty());
    }

    @Test
    void confirmRegistration_登録完了メールの送信に失敗しても本登録は成功する() {
        Instant now = Instant.now();
        service.requestVerification(new Email("taro@example.com"), "s3cret", "山田太郎", now);
        String code = emailVerificationRepository.findByEmail(new Email("taro@example.com"))
                .orElseThrow().verificationCode();

        // 完了メールは「お知らせ」であり、本登録が済んだ事実には影響しない
        EmailVerificationService completionMailFails = new EmailVerificationService(
                emailVerificationRepository, customerRegistrationService, FAKE_HASHER, FAILING_SENDER);
        Customer customer = completionMailFails.confirmRegistration(new Email("taro@example.com"), code, now);

        assertEquals("山田太郎", customer.displayName());
        assertTrue(customerRepository.findByEmail(new Email("taro@example.com")).isPresent());
        assertTrue(emailVerificationRepository.findByEmail(new Email("taro@example.com")).isEmpty());
    }
}
