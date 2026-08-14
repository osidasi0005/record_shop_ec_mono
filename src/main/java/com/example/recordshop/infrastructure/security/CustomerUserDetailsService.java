package com.example.recordshop.infrastructure.security;

import com.example.recordshop.domain.customer.CustomerRepository;
import com.example.recordshop.domain.customer.Email;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomerUserDetailsService implements UserDetailsService {

    private final CustomerRepository customerRepository;

    public CustomerUserDetailsService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return customerRepository.findByEmail(new Email(email))
                .map(CustomerUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("No customer for email: " + email));
    }
}
