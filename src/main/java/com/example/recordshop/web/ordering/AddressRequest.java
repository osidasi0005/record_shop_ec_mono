package com.example.recordshop.web.ordering;

import com.example.recordshop.domain.shared.Address;

public record AddressRequest(
        String recipientName,
        String postalCode,
        String prefecture,
        String city,
        String addressLine,
        String country
) {
    public Address toDomain() {
        return new Address(recipientName, postalCode, prefecture, city, addressLine, country);
    }
}
