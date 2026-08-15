package com.example.recordshop.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;

/**
 * AWS SESクライアントのBean定義。認証情報はデフォルトクレデンシャルプロバイダチェーンに委ねるため、
 * ここではリージョンのみを明示指定する({@link com.example.recordshop.config.SecurityConfig}が
 * PasswordEncoderのBeanを定義するのと同じ「技術別Configクラス」パターン)。
 */
@Configuration
public class MailConfig {

    @Bean
    public SesClient sesClient(@Value("${app.mail.aws.region}") String region) {
        return SesClient.builder().region(Region.of(region)).build();
    }
}
