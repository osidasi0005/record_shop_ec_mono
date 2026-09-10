package com.example.recordshop.infrastructure.security;

import com.example.recordshop.domain.customer.Customer;
import com.example.recordshop.domain.customer.CustomerId;
import com.example.recordshop.domain.customer.CustomerRepository;
import com.example.recordshop.domain.customer.CustomerRole;
import com.example.recordshop.domain.customer.Email;
import com.example.recordshop.domain.customer.PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 起動時にADMINロールの会員を1件だけ用意する。
 *
 * <p>自己登録フロー({@link com.example.recordshop.domain.customer.CustomerRegistrationService})は
 * 常にCUSTOMERロールで会員を作る設計のため、管理画面に入るための最初のADMINアカウントは
 * この起動時シーダーで用意するしかない({@link Customer#reconstitute} を使って直接ADMINロールで作成する)。
 *
 * <p>{@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD} の両方が設定されている場合のみ動作し、
 * 既に同じEmailの会員が存在する場合は何もしない(冪等)。
 */
@Component
public class AdminAccountSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountSeeder.class);

    private final CustomerRepository customerRepository;
    private final PasswordHasher passwordHasher;
    private final String adminEmail;
    private final String adminPassword;

    public AdminAccountSeeder(CustomerRepository customerRepository, PasswordHasher passwordHasher,
                               @Value("${app.admin.email:}") String adminEmail,
                               @Value("${app.admin.password:}") String adminPassword) {
        this.customerRepository = customerRepository;
        this.passwordHasher = passwordHasher;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.warn("ADMIN_EMAIL / ADMIN_PASSWORD が未設定のため、管理者アカウントの起票をスキップしました。"
                    + "管理画面(/admin/**)を使うには環境変数を設定して再起動してください。");
            return;
        }

        Email email = new Email(adminEmail);
        if (customerRepository.existsByEmail(email)) {
            log.info("管理者アカウント({})は既に存在するため起票をスキップしました。", email);
            return;
        }

        Customer admin = Customer.reconstitute(
                CustomerId.generate(),
                email,
                passwordHasher.hash(adminPassword),
                "管理者",
                CustomerRole.ADMIN,
                Instant.now()
        );
        customerRepository.save(admin);
        log.info("管理者アカウント({})を起票しました。", email);
    }
}
