package com.example.recordshop.infrastructure.mybatis;

import com.example.recordshop.domain.customer.Customer;
import com.example.recordshop.domain.customer.CustomerId;
import com.example.recordshop.domain.customer.CustomerRepository;
import com.example.recordshop.domain.customer.CustomerRole;
import com.example.recordshop.domain.customer.Email;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * {@link CustomerRepository}(ドメイン層のポート)のMyBatisアダプタ実装。
 * emailの一意性はDB側のUNIQUE制約でも保証されるが、{@link CustomerRegistrationService}が
 * 事前にexistsByEmailでチェックする設計は変えていない(JPA版と同じ二重の安全網)。
 */
@Repository
public class MyBatisCustomerRepository implements CustomerRepository {

    private final CustomerMapper mapper;

    public MyBatisCustomerRepository(CustomerMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void save(Customer customer) {
        mapper.insert(new CustomerRow(
                customer.customerId().value(), customer.email().value(), customer.passwordHash(),
                customer.displayName(), customer.role().name(), customer.registeredAt()
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> findById(CustomerId customerId) {
        CustomerRow row = mapper.selectById(customerId.value());
        return Optional.ofNullable(row).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> findByEmail(Email email) {
        CustomerRow row = mapper.selectByEmail(email.value());
        return Optional.ofNullable(row).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(Email email) {
        return mapper.countByEmail(email.value()) > 0;
    }

    private Customer toDomain(CustomerRow row) {
        return Customer.reconstitute(
                new CustomerId(row.id()), new Email(row.email()), row.passwordHash(), row.displayName(),
                CustomerRole.valueOf(row.role()), row.registeredAt()
        );
    }
}
