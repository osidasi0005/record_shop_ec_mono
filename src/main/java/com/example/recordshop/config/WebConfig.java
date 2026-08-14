package com.example.recordshop.config;

import com.example.recordshop.infrastructure.web.CloudFrontProtoFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class WebConfig {

    /**
     * Spring Securityのフィルタチェーンより前に確実に動かす必要があるため、
     * 最優先(HIGHEST_PRECEDENCE)で登録する。
     */
    @Bean
    public FilterRegistrationBean<CloudFrontProtoFilter> cloudFrontProtoFilter() {
        FilterRegistrationBean<CloudFrontProtoFilter> registration =
                new FilterRegistrationBean<>(new CloudFrontProtoFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
