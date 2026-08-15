package com.example.recordshop.infrastructure.mail;

import com.example.recordshop.domain.customer.Email;
import com.example.recordshop.domain.customer.EmailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

/**
 * {@link EmailSender}(ドメイン層のポート)のAWS SES(Simple Email Service)アダプタ実装。
 * SesClientの認証情報は明示指定せず、AWS SDKのデフォルトクレデンシャルプロバイダチェーンに委ねる
 * (ECS Fargate上ではタスクロール、ローカルでは ~/.aws/credentials やSSO)。
 */
@Component
public class SesEmailSender implements EmailSender {

    private final SesClient sesClient;
    private final String fromAddress;

    public SesEmailSender(SesClient sesClient, @Value("${app.mail.from-address}") String fromAddress) {
        this.sesClient = sesClient;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendVerificationCode(Email to, String displayName, String verificationCode) {
        send(to, "【Record Shop】会員登録の確認コード",
                displayName + " 様\n\n以下の確認コードを会員登録画面に入力してください。\n\n確認コード: "
                        + verificationCode + "\n\n有効期限は10分間です。");
    }

    @Override
    public void sendRegistrationCompleted(Email to, String displayName) {
        send(to, "【Record Shop】会員登録が完了しました",
                displayName + " 様\n\n会員登録が完了しました。ログインしてご利用いただけます。");
    }

    private void send(Email to, String subject, String bodyText) {
        SendEmailRequest request = SendEmailRequest.builder()
                .source(fromAddress)
                .destination(Destination.builder().toAddresses(to.value()).build())
                .message(Message.builder()
                        .subject(Content.builder().data(subject).charset("UTF-8").build())
                        .body(Body.builder()
                                .text(Content.builder().data(bodyText).charset("UTF-8").build())
                                .build())
                        .build())
                .build();
        sesClient.sendEmail(request);
    }
}
