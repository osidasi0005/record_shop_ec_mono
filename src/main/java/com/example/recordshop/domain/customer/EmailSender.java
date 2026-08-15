package com.example.recordshop.domain.customer;

/**
 * 会員登録に伴うメール送信を行うポート(インターフェースのみ)。
 *
 * <p>{@link PasswordHasher} と同様、具体的な送信技術(AWS SES等)にドメイン層を
 * 依存させないための抽象化。実装はインフラ層({@code infrastructure.mail})に置く。
 */
public interface EmailSender {

    /** 確認コード(6桁の数字)を送信する。 */
    void sendVerificationCode(Email to, String displayName, String verificationCode);

    /** 会員登録完了を通知するメールを送信する。 */
    void sendRegistrationCompleted(Email to, String displayName);
}
