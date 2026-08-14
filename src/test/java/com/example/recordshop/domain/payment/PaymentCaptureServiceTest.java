package com.example.recordshop.domain.payment;

import com.example.recordshop.domain.catalog.Format;
import com.example.recordshop.domain.catalog.MediaType;
import com.example.recordshop.domain.catalog.Speed;
import com.example.recordshop.domain.customer.CustomerId;
import com.example.recordshop.domain.inventory.ConditionType;
import com.example.recordshop.domain.inventory.ListingId;
import com.example.recordshop.domain.ordering.*;
import com.example.recordshop.domain.shared.Address;
import com.example.recordshop.domain.shared.InvariantViolationException;
import com.example.recordshop.domain.shared.Money;
import com.example.recordshop.infrastructure.memory.InMemoryOrderRepository;
import com.example.recordshop.infrastructure.memory.InMemoryPaymentRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentCaptureServiceTest {

    private final InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
    private final InMemoryPaymentRepository paymentRepository = new InMemoryPaymentRepository();
    private final PaymentCaptureService service = new PaymentCaptureService(orderRepository, paymentRepository);

    private Address address() {
        return new Address("山田 太郎", "150-0001", "東京都", "渋谷区", "1-2-3", "JP");
    }

    private Order pendingOrder() {
        PressingSnapshot snapshot = new PressingSnapshot(
                "Kind of Blue", "Miles Davis", "Columbia", "CL 1355", "US", 1959,
                Format.vinyl(MediaType.LP, Speed.RPM_33, 1),
                ConditionType.NEW, null, null);
        OrderLine line = new OrderLine(ListingId.generate(), snapshot, Money.jpy(4200), 2);
        Order order = Order.place(OrderId.generate(), CustomerId.generate(),
                List.of(line), address(), address(), Instant.now());
        orderRepository.save(order);
        return order;
    }

    @Test
    void capturePayment_Order合計金額でCaptureされOrderがPAIDになる() {
        Order order = pendingOrder();

        Payment payment = service.capturePayment(order.orderId(), PaymentMethod.CREDIT_CARD, Instant.now());

        assertEquals(PaymentStatus.CAPTURED, payment.status());
        assertEquals(order.totalAmount(), payment.amount());

        Order reloaded = orderRepository.findById(order.orderId()).orElseThrow();
        assertEquals(OrderStatus.PAID, reloaded.status());
    }

    @Test
    void capturePayment_存在しないOrderは拒否される() {
        assertThrows(InvariantViolationException.class,
                () -> service.capturePayment(OrderId.generate(), PaymentMethod.CREDIT_CARD, Instant.now()));
    }

    @Test
    void capturePayment_既にPAID済みのOrderは二重決済できない() {
        Order order = pendingOrder();
        service.capturePayment(order.orderId(), PaymentMethod.CREDIT_CARD, Instant.now());

        assertThrows(RuntimeException.class,
                () -> service.capturePayment(order.orderId(), PaymentMethod.CREDIT_CARD, Instant.now()));
    }
}
