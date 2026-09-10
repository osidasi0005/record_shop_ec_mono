package com.example.recordshop.infrastructure.security;

import com.example.recordshop.domain.customer.Customer;
import com.example.recordshop.domain.customer.CustomerId;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security の {@link UserDetails} に {@link Customer} を適合させるアダプタ。
 *
 * <p>ログイン後、コントローラー側で {@code (CustomerUserDetails) authentication.getPrincipal()} として
 * キャストすることで、都度リポジトリを引かずに {@link #customerId()} や表示名を取り出せる。
 */
public class CustomerUserDetails implements UserDetails {

    private final CustomerId customerId;
    private final String email;
    private final String passwordHash;
    private final String displayName;
    private final List<GrantedAuthority> authorities;

    public CustomerUserDetails(Customer customer) {
        this.customerId = customer.customerId();
        this.email = customer.email().value();
        this.passwordHash = customer.passwordHash();
        this.displayName = customer.displayName();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + customer.role().name()));
    }

    public CustomerId customerId() {
        return customerId;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
