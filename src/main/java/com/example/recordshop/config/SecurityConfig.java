package com.example.recordshop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 会員向け画面(Thymeleaf)はフォーム認証で保護し、既存の REST API(/api/**)は
 * 「歩く骨格」フェーズ以来の方針どおり認証なしでアクセスできる状態を維持する
 * (curlでの動作確認・自動テストの互換性を崩さないための意図的な判断)。
 *
 * <p>/api/** は CSRF トークンを持たない外部クライアント(curl等)からの利用を想定しているため
 * CSRF保護の対象から除外する。画面(Thymeleafフォーム)側は通常どおりCSRF保護を効かせる。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/catalog/**", "/register", "/login", "/error",
                                "/css/**", "/js/**", "/images/**", "/webjars/**", "/actuator/health", "/api/**",
                                "/robots.txt").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/catalog", false)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/catalog")
                        .permitAll()
                );

        return http.build();
    }
}
