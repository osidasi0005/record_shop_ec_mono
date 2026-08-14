package com.example.recordshop.infrastructure.mybatis;

import com.example.recordshop.domain.catalog.Format;
import com.example.recordshop.domain.catalog.MediaType;
import com.example.recordshop.domain.catalog.Speed;
import com.example.recordshop.domain.customer.CustomerId;
import com.example.recordshop.domain.inventory.ConditionType;
import com.example.recordshop.domain.inventory.GoldmineGrade;
import com.example.recordshop.domain.inventory.ListingId;
import com.example.recordshop.domain.ordering.Order;
import com.example.recordshop.domain.ordering.OrderId;
import com.example.recordshop.domain.ordering.OrderLine;
import com.example.recordshop.domain.ordering.OrderRepository;
import com.example.recordshop.domain.ordering.OrderStatus;
import com.example.recordshop.domain.ordering.PressingSnapshot;
import com.example.recordshop.domain.shared.Address;
import com.example.recordshop.domain.shared.Money;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link OrderRepository}(ドメイン層のポート)のMyBatisアダプタ実装。
 *
 * <p>{@link #findByCustomerId(CustomerId)} は、Release/Listingと同じ原則で「N+1にしない」設計を
 * 徹底している。注文件数分ループして{@code selectOrderLinesByOrderId}を呼ぶと典型的なN+1になるため、
 * 該当する注文IDをまとめてIN句で1回のSELECTにし、Java側でグルーピングする。
 *
 * <p>比較実験の簡略化として新規登録のみを想定する(Release/Listingと同様、更新は対象外)。
 */
@Repository
public class MyBatisOrderRepository implements OrderRepository {

    private final OrderMapper mapper;

    public MyBatisOrderRepository(OrderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void save(Order order) {
        mapper.insertOrder(toOrderRow(order));
        for (OrderLine line : order.lines()) {
            mapper.insertOrderLine(toOrderLineRow(order.orderId().value(), line));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(OrderId orderId) {
        OrderRow row = mapper.selectOrderById(orderId.value());
        if (row == null) {
            return Optional.empty();
        }
        List<OrderLine> lines = mapper.selectOrderLinesByOrderId(orderId.value()).stream()
                .map(this::toDomainLine)
                .toList();
        return Optional.of(toDomain(row, lines));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByCustomerId(CustomerId customerId) {
        List<OrderRow> orderRows = mapper.selectOrdersByCustomerId(customerId.value());
        if (orderRows.isEmpty()) {
            return List.of();
        }
        List<UUID> orderIds = orderRows.stream().map(OrderRow::id).toList();

        Map<UUID, List<OrderLine>> linesByOrder = new HashMap<>();
        for (OrderLineRow lineRow : mapper.selectOrderLinesByOrderIds(orderIds)) {
            linesByOrder.computeIfAbsent(lineRow.orderId(), k -> new ArrayList<>()).add(toDomainLine(lineRow));
        }

        List<Order> orders = new ArrayList<>();
        for (OrderRow row : orderRows) {
            orders.add(toDomain(row, linesByOrder.getOrDefault(row.id(), List.of())));
        }
        return orders;
    }

    private OrderRow toOrderRow(Order order) {
        Address ship = order.shippingAddress();
        Address bill = order.billingAddress();
        return new OrderRow(
                order.orderId().value(), order.customerId().value(), order.status().name(), order.placedAt(),
                ship.recipientName(), ship.postalCode(), ship.prefecture(), ship.city(), ship.addressLine(), ship.country(),
                bill.recipientName(), bill.postalCode(), bill.prefecture(), bill.city(), bill.addressLine(), bill.country()
        );
    }

    private OrderLineRow toOrderLineRow(UUID orderId, OrderLine line) {
        PressingSnapshot s = line.pressingSnapshot();
        return new OrderLineRow(
                orderId, line.listingId().value(), s.releaseTitle(), s.artistName(), s.labelName(),
                s.catalogNumber(), s.country(), s.pressYear(), s.format().mediaType().name(),
                s.format().speed().name(), s.format().discCount(), s.conditionType().name(),
                s.vinylGrade() == null ? null : s.vinylGrade().name(),
                s.sleeveGrade() == null ? null : s.sleeveGrade().name(),
                line.unitPrice().amount(), line.unitPrice().currency().getCurrencyCode(), line.quantity()
        );
    }

    private Order toDomain(OrderRow row, List<OrderLine> lines) {
        Address shipping = new Address(row.shipRecipientName(), row.shipPostalCode(), row.shipPrefecture(),
                row.shipCity(), row.shipAddressLine(), row.shipCountry());
        Address billing = new Address(row.billRecipientName(), row.billPostalCode(), row.billPrefecture(),
                row.billCity(), row.billAddressLine(), row.billCountry());
        return Order.reconstitute(new OrderId(row.id()), new CustomerId(row.customerId()), lines,
                shipping, billing, OrderStatus.valueOf(row.status()), row.placedAt());
    }

    private OrderLine toDomainLine(OrderLineRow row) {
        PressingSnapshot snapshot = new PressingSnapshot(
                row.releaseTitle(), row.artistName(), row.labelName(), row.catalogNumber(), row.pressingCountry(),
                row.pressYear(),
                new Format(MediaType.valueOf(row.mediaType()), Speed.valueOf(row.speed()), row.discCount()),
                ConditionType.valueOf(row.conditionType()),
                row.vinylGrade() == null ? null : GoldmineGrade.valueOf(row.vinylGrade()),
                row.sleeveGrade() == null ? null : GoldmineGrade.valueOf(row.sleeveGrade())
        );
        Money unitPrice = new Money(row.unitPriceAmount(), Currency.getInstance(row.unitPriceCurrency()));
        return new OrderLine(new ListingId(row.listingId()), snapshot, unitPrice, row.quantity());
    }
}
