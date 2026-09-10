package com.example.recordshop.infrastructure.security;

import com.example.recordshop.domain.customer.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * {@link PasswordHasher}(ドメイン層のポート)の Spring Security アダプタ実装。
 * 実際のハッシュアルゴリズム(BCrypt)は {@link org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder}
 * に委譲する(Bean定義は {@link com.example.recordshop.config.SecurityConfig} 側)。
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    public BCryptPasswordHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hash) {
        return passwordEncoder.matches(rawPassword, hash);
    }
}
