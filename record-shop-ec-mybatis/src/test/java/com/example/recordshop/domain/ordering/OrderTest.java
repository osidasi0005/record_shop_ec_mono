package com.example.recordshop.domain.ordering;

import com.example.recordshop.domain.catalog.Format;
import com.example.recordshop.domain.catalog.MediaType;
import com.example.recordshop.domain.catalog.Speed;
import com.example.recordshop.domain.customer.CustomerId;
import com.example.recordshop.domain.inventory.ConditionType;
import com.example.recordshop.domain.inventory.ListingId;
import com.example.recordshop.domain.shared.Address;
import com.example.recordshop.domain.shared.IllegalStateTransitionException;
import com.example.recordshop.domain.shared.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {

    private Address address(String city) {
        return new Address("山田 太郎", "150-0001", "東京都", city, "1-2-3", "JP");
    }

    private OrderLine line(Money unitPrice, int quantity) {
        PressingSnapshot snapshot = new PressingSnapshot(
                "Kind of Blue", "Miles Davis", "Columbia", "CL 1355", "US", 1959,
                Format.vinyl(MediaType.LP, Speed.RPM_33, 1),
                ConditionType.NEW, null, null);
        return new OrderLine(ListingId.generate(), snapshot, unitPrice, quantity);
    }

    private Order pendingOrder() {
        return Order.place(OrderId.generate(), CustomerId.generate(),
                List.of(line(Money.jpy(4200), 2)), address("渋谷区"), address("渋谷区"), Instant.now());
    }

    @Test
    void totalAmount_明細の合計になる() {
        Order order = pendingOrder();
        assertEquals(Money.jpy(8400), order.totalAmount());
    }

    @Test
    void changeShippingAddress_PendingとPaidの間は変更できる() {
        Order order = pendingOrder();

        order.changeShippingAddress(address("大阪市"), Instant.now());
        assertEquals("大阪市", order.shippingAddress().city());

        order.markPaid();
        order.changeShippingAddress(address("札幌市"), Instant.now());
        assertEquals("札幌市", order.shippingAddress().city());
    }

    @Test
    void changeShippingAddress_発送後は変更できない() {
        Order order = pendingOrder();
        order.markPaid();
        order.markShipped();

        assertThrows(IllegalStateTransitionException.class,
                () -> order.changeShippingAddress(address("札幌市"), Instant.now()));
    }

    @Test
    void changeBillingAddress_決済確定後は変更できない() {
        Order order = pendingOrder();
        order.markPaid();

        assertThrows(IllegalStateTransitionException.class,
                () -> order.changeBillingAddress(address("札幌市")));
    }

    @Test
    void 発送後は注文をキャンセルできない() {
        Order order = pendingOrder();
        order.markPaid();
        order.markShipped();

        assertThrows(IllegalStateTransitionException.class, order::cancel);
    }
}
