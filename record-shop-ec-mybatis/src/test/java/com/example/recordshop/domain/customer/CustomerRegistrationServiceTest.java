package com.example.recordshop.domain.customer;

import com.example.recordshop.domain.shared.InvariantViolationException;
import com.example.recordshop.infrastructure.memory.InMemoryCustomerRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerRegistrationServiceTest {

    /** テストでは実際のBCrypt等に依存させず、単純な変換で代用する。 */
    private static final PasswordHasher FAKE_HASHER = new PasswordHasher() {
        @Override
        public String hash(String rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String hash) {
            return hash.equals("hashed:" + rawPassword);
        }
    };

    private final InMemoryCustomerRepository customerRepository = new InMemoryCustomerRepository();
    private final CustomerRegistrationService service =
            new CustomerRegistrationService(customerRepository, FAKE_HASHER);

    @Test
    void register_パスワードはハッシュ化されて平文のまま保持されない() {
        Customer customer = service.register(new Email("taro@example.com"), "s3cret", "山田太郎", Instant.now());

        assertNotEquals("s3cret", customer.passwordHash());
        assertEquals("hashed:s3cret", customer.passwordHash());
    }

    @Test
    void register_同じEmailは二重登録できない() {
        service.register(new Email("taro@example.com"), "s3cret", "山田太郎", Instant.now());

        assertThrows(InvariantViolationException.class,
                () -> service.register(new Email("taro@example.com"), "other", "別の名前", Instant.now()));
    }
}
