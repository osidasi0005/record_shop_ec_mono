package com.example.recordshop.infrastructure.mybatis;

import com.example.recordshop.domain.ordering.OrderId;
import com.example.recordshop.domain.payment.Payment;
import com.example.recordshop.domain.payment.PaymentId;
import com.example.recordshop.domain.payment.PaymentMethod;
import com.example.recordshop.domain.payment.PaymentRepository;
import com.example.recordshop.domain.payment.PaymentStatus;
import com.example.recordshop.domain.shared.Money;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.List;
import java.util.Optional;

/**
 * {@link PaymentRepository}(ドメイン層のポート)のMyBatisアダプタ実装。
 * Release/Order/Listingと同様、比較実験の簡略化として新規登録のみを想定する。
 */
@Repository
public class MyBatisPaymentRepository implements PaymentRepository {

    private final PaymentMapper mapper;

    public MyBatisPaymentRepository(PaymentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void save(Payment payment) {
        mapper.insert(new PaymentRow(
                payment.paymentId().value(), payment.orderId().value(),
                payment.amount().amount(), payment.amount().currency().getCurrencyCode(),
                payment.method().name(), payment.status().name(),
                payment.capturedAt().orElse(null)
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findById(PaymentId paymentId) {
        PaymentRow row = mapper.selectById(paymentId.value());
        return Optional.ofNullable(row).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payment> findByOrderId(OrderId orderId) {
        return mapper.selectByOrderId(orderId.value()).stream().map(this::toDomain).toList();
    }

    private Payment toDomain(PaymentRow row) {
        Money amount = new Money(row.amountAmount(), Currency.getInstance(row.amountCurrency()));
        return Payment.reconstitute(
                new PaymentId(row.id()), new OrderId(row.orderId()), amount,
                PaymentMethod.valueOf(row.method()), PaymentStatus.valueOf(row.status()), row.capturedAt()
        );
    }
}
