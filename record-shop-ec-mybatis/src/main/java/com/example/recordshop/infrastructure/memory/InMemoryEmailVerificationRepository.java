package com.example.recordshop.infrastructure.memory;

import com.example.recordshop.domain.customer.Email;
import com.example.recordshop.domain.customer.EmailVerification;
import com.example.recordshop.domain.customer.EmailVerificationRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryEmailVerificationRepository implements EmailVerificationRepository {

    private final Map<Email, EmailVerification> store = new LinkedHashMap<>();

    @Override
    public void save(EmailVerification emailVerification) {
        store.put(emailVerification.email(), emailVerification);
    }

    @Override
    public Optional<EmailVerification> findByEmail(Email email) {
        return Optional.ofNullable(store.get(email));
    }

    @Override
    public void deleteByEmail(Email email) {
        store.remove(email);
    }
}
