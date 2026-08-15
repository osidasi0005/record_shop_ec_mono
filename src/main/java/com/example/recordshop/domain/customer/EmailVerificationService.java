package com.example.recordshop.domain.customer;

import com.example.recordshop.domain.shared.InvariantViolationException;

import java.time.Duration;
import java.time.Instant;

/**
 * 会員登録の確認コード発行・照合を調停するドメインサービス。
 *
 * <p>「Emailがシステム全体で一意である」という不変条件のチェックと実際のCustomer生成は
 * {@link CustomerRegistrationService}の責務のまま残し、このサービスはその前段(確認コードの
 * 発行・送信)と後段(照合成功後の本登録呼び出し)を2フェーズとして担当する。
 * 責務を分散させず、不変条件の実装は1箇所({@link CustomerRegistrationService})に閉じ込める。
 */
public final class EmailVerificationService {

    private static final Duration CODE_TTL = Duration.ofMinutes(10);

    private final EmailVerificationRepository emailVerificationRepository;
    private final CustomerRegistrationService customerRegistrationService;
    private final PasswordHasher passwordHasher;
    private final EmailSender emailSender;

    public EmailVerificationService(EmailVerificationRepository emailVerificationRepository,
                                     CustomerRegistrationService customerRegistrationService,
                                     PasswordHasher passwordHasher, EmailSender emailSender) {
        this.emailVerificationRepository = emailVerificationRepository;
        this.customerRegistrationService = customerRegistrationService;
        this.passwordHasher = passwordHasher;
        this.emailSender = emailSender;
    }

    /**
     * フェーズ1: 確認コードを発行してメール送信する。
     * 同一メールアドレスで既に仮登録があれば、上書き(コード再発行)する。
     */
    public void requestVerification(Email email, String rawPassword, String displayName, Instant now) {
        customerRegistrationService.assertEmailAvailable(email);

        String passwordHash = passwordHasher.hash(rawPassword);
        EmailVerification verification = EmailVerification.issue(
                EmailVerificationId.generate(), email, passwordHash, displayName, now, CODE_TTL);
        emailVerificationRepository.save(verification);
        emailSender.sendVerificationCode(email, displayName, verification.verificationCode());
    }

    /**
     * フェーズ2: 確認コードを照合する。成功したらCustomerを本登録し、仮登録レコードを削除、
     * 登録完了メールを送信する。
     */
    public Customer confirmRegistration(Email email, String inputCode, Instant now) {
        EmailVerification verification = emailVerificationRepository.findByEmail(email)
                .orElseThrow(() -> new InvariantViolationException(
                        "確認コードの発行履歴が見つかりません。もう一度会員登録をやり直してください"));

        try {
            verification.confirm(inputCode, now);
        } catch (InvariantViolationException e) {
            emailVerificationRepository.save(verification);
            throw e;
        }

        Customer customer = customerRegistrationService.registerWithHashedPassword(
                verification.email(), verification.passwordHash(), verification.displayName(), now);
        emailVerificationRepository.deleteByEmail(email);
        emailSender.sendRegistrationCompleted(customer.email(), customer.displayName());
        return customer;
    }
}
