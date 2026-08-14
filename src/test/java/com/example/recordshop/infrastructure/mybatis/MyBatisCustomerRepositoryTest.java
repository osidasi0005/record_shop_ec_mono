package com.example.recordshop.infrastructure.mybatis;

import com.example.recordshop.domain.customer.Customer;
import com.example.recordshop.domain.customer.CustomerId;
import com.example.recordshop.domain.customer.Email;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class MyBatisCustomerRepositoryTest {

    @Autowired
    private MyBatisCustomerRepository repository;

    @Test
    void save_findById_で保存したCustomerが同じ内容で復元できる() {
        Customer customer = Customer.register(CustomerId.generate(), new Email("taro@example.com"),
                "hashed-password", "山田太郎", Instant.now());

        repository.save(customer);
        Customer found = repository.findById(customer.customerId()).orElseThrow();

        assertThat(found.email()).isEqualTo(new Email("taro@example.com"));
        assertThat(found.displayName()).isEqualTo("山田太郎");
    }

    @Test
    void existsByEmail_登録済みのEmailはtrue_未登録はfalse() {
        Customer customer = Customer.register(CustomerId.generate(), new Email("taro@example.com"),
                "hashed-password", "山田太郎", Instant.now());
        repository.save(customer);

        assertThat(repository.existsByEmail(new Email("taro@example.com"))).isTrue();
        assertThat(repository.existsByEmail(new Email("jiro@example.com"))).isFalse();
    }
}
