package com.example.recordshop.domain.customer;

import com.example.recordshop.domain.shared.InvariantViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailVerificationService.class);

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
     *
     * <p>送信 → 保存の順に実行する。逆順(保存 → 送信)にすると、送信に失敗したときに
     * 「利用者は確認コードを受け取っていないのに仮登録レコードだけ残る」状態になり、
     * 再登録の導線も既存レコードを上書きするまで直らないため。
     *
     * @throws EmailDeliveryException 確認コードメールの送信に失敗した場合(仮登録は作成されない)
     */
    public void requestVerification(Email email, String rawPassword, String displayName, Instant now) {
        customerRegistrationService.assertEmailAvailable(email);

        String passwordHash = passwordHasher.hash(rawPassword);
        EmailVerification verification = EmailVerification.issue(
                EmailVerificationId.generate(), email, passwordHash, displayName, now, CODE_TTL);
        emailSender.sendVerificationCode(email, displayName, verification.verificationCode());
        emailVerificationRepository.save(verification);
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

        // 登録完了メールは「お知らせ」であり、本登録が済んだ事実には影響しない。
        // ここで送信失敗を伝播させると、登録できているのに利用者にはエラーが見えることになる。
        try {
            emailSender.sendRegistrationCompleted(customer.email(), customer.displayName());
        } catch (EmailDeliveryException e) {
            LOGGER.warn("会員登録は完了しましたが、完了通知メールの送信に失敗しました: {}", customer.email().value(), e);
        }
        return customer;
    }
}
