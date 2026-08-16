package com.example.recordshop.infrastructure.mail;

import com.example.recordshop.domain.customer.Email;
import com.example.recordshop.domain.customer.EmailDeliveryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.MessageRejectedException;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SesEmailSenderTest {

    private static final String FROM_ADDRESS = "no-reply@example.com";

    @Mock
    private SesClient sesClient;

    @Test
    void sendVerificationCode_正しい宛先_送信元_確認コードを含む本文で送信される() {
        SesEmailSender sender = new SesEmailSender(sesClient, FROM_ADDRESS);

        sender.sendVerificationCode(new Email("taro@example.com"), "山田太郎", "123456");

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(captor.capture());
        SendEmailRequest request = captor.getValue();

        assertThat(request.source()).isEqualTo(FROM_ADDRESS);
        assertThat(request.destination().toAddresses()).containsExactly("taro@example.com");
        assertThat(request.message().body().text().data()).contains("123456");
    }

    @Test
    void sendRegistrationCompleted_正しい宛先_送信元で送信される() {
        SesEmailSender sender = new SesEmailSender(sesClient, FROM_ADDRESS);

        sender.sendRegistrationCompleted(new Email("taro@example.com"), "山田太郎");

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(captor.capture());
        SendEmailRequest request = captor.getValue();

        assertThat(request.source()).isEqualTo(FROM_ADDRESS);
        assertThat(request.destination().toAddresses()).containsExactly("taro@example.com");
        assertThat(request.message().body().text().data()).contains("山田太郎");
    }

    @Test
    void 送信に失敗したらSDK例外ではなくEmailDeliveryExceptionを投げる() {
        // SESサンドボックス中の未検証宛先はMessageRejectedExceptionになる。
        // SDK固有の例外がそのまま伝播すると呼び出し元が捕まえられず500になってしまう。
        SesEmailSender sender = new SesEmailSender(sesClient, FROM_ADDRESS);
        given(sesClient.sendEmail(any(SendEmailRequest.class)))
                .willThrow(MessageRejectedException.builder().message("Email address is not verified").build());

        assertThatThrownBy(() -> sender.sendVerificationCode(new Email("taro@example.com"), "山田太郎", "123456"))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("taro@example.com");
    }
}
