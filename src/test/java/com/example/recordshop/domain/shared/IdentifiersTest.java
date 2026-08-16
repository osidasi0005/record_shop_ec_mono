package com.example.recordshop.domain.shared;

import com.example.recordshop.domain.catalog.ReleaseId;
import com.example.recordshop.domain.inventory.ListingId;
import com.example.recordshop.domain.ordering.OrderId;
import com.example.recordshop.domain.payment.PaymentId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentifiersTest {

    @Test
    void parse_正しいUUID文字列はそのまま変換される() {
        UUID uuid = UUID.randomUUID();

        assertEquals(uuid, Identifiers.parse(uuid.toString(), "ReleaseId"));
    }

    @Test
    void parse_UUIDでない文字列はMalformedIdentifierException() {
        MalformedIdentifierException e = assertThrows(MalformedIdentifierException.class,
                () -> Identifiers.parse("999999", "ReleaseId"));

        assertEquals("ReleaseId の形式が不正です: 999999", e.getMessage());
    }

    @Test
    void parse_nullもMalformedIdentifierException() {
        assertThrows(MalformedIdentifierException.class, () -> Identifiers.parse(null, "ReleaseId"));
    }

    @Test
    void 各IDのofも不正な文字列でMalformedIdentifierExceptionを投げる() {
        // Web層はこの例外を 404 Not Found に変換する(ApiExceptionHandler / PathIds)。
        assertThrows(MalformedIdentifierException.class, () -> ReleaseId.of("999999"));
        assertThrows(MalformedIdentifierException.class, () -> ListingId.of("nonexistent-id"));
        assertThrows(MalformedIdentifierException.class, () -> OrderId.of("nonexistent-id"));
        assertThrows(MalformedIdentifierException.class, () -> PaymentId.of("nonexistent-id"));
    }
}
