package com.example.recordshop.infrastructure.mail;

import com.example.recordshop.domain.customer.Email;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

import static org.assertj.core.api.Assertions.assertThat;
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
}
