package com.example.recordshop.config;

import com.example.recordshop.domain.catalog.ReleaseRepository;
import com.example.recordshop.domain.customer.CustomerRegistrationService;
import com.example.recordshop.domain.customer.CustomerRepository;
import com.example.recordshop.domain.customer.PasswordHasher;
import com.example.recordshop.domain.inventory.ListingRepository;
import com.example.recordshop.domain.ordering.OrderPlacementService;
import com.example.recordshop.domain.ordering.OrderRepository;
import com.example.recordshop.domain.payment.PaymentCaptureService;
import com.example.recordshop.domain.payment.PaymentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ドメインサービスは Spring 非依存の素の Java クラスなので、Bean登録だけをここに切り出す。
 * (ドメイン層自体に {@code @Service} 等のアノテーションは付けない)
 *
 * <p>各Repositoryインターフェースの実装はSpringが自動でMyBatisアダプタ({@code infrastructure/mybatis}
 * 配下)を注入する。JPA版とこのファイルが一字一句同じなのは、ドメイン層・DIの境界が
 * 永続化技術に一切依存していないことの裏付けでもある。
 */
@Configuration
public class DomainServiceConfig {

    @Bean
    public OrderPlacementService orderPlacementService(ListingRepository listingRepository,
                                                         ReleaseRepository releaseRepository,
                                                         OrderRepository orderRepository) {
        return new OrderPlacementService(listingRepository, releaseRepository, orderRepository);
    }

    @Bean
    public PaymentCaptureService paymentCaptureService(OrderRepository orderRepository,
                                                         PaymentRepository paymentRepository) {
        return new PaymentCaptureService(orderRepository, paymentRepository);
    }

    @Bean
    public CustomerRegistrationService customerRegistrationService(CustomerRepository customerRepository,
                                                                     PasswordHasher passwordHasher) {
        return new CustomerRegistrationService(customerRepository, passwordHasher);
    }
}
