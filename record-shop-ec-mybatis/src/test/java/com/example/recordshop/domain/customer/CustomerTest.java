package com.example.recordshop.domain.customer;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerTest {

    @Test
    void register_一般会員はCUSTOMERロールになる() {
        Customer customer = Customer.register(CustomerId.generate(), new Email("Taro@Example.com"),
                "hashed-password", "山田太郎", Instant.now());

        assertEquals(CustomerRole.CUSTOMER, customer.role());
        assertEquals("taro@example.com", customer.email().value());
    }

    @Test
    void email_不正な形式は拒否される() {
        assertThrows(IllegalArgumentException.class, () -> new Email("not-an-email"));
    }

    @Test
    void changeDisplayName_表示名を変更できる() {
        Customer customer = Customer.register(CustomerId.generate(), new Email("taro@example.com"),
                "hashed-password", "山田太郎", Instant.now());

        customer.changeDisplayName("山田次郎");

        assertEquals("山田次郎", customer.displayName());
    }
}
