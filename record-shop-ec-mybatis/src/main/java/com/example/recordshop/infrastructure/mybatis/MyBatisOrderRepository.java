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
 * <p>{@link #findByCustomerId(CustomerId)} と {@link #findAll()} は、Release/Listingと同じ原則で
 * 「N+1にしない」設計を徹底している。注文件数分ループして{@code selectOrderLinesByOrderId}を呼ぶと
 * 典型的なN+1になるため、該当する注文IDをまとめてIN句で1回のSELECTにし、Java側でグルーピングする。
 *
 * <p>{@link #save(Order)} は新規登録・更新の両方に対応する。決済確定(markPaid)等で
 * 既存Orderのstatusが変わった後の再saveは、既存行の有無をSELECTで判定してUPDATEに振り分ける
 * (dirty checkingが無いMyBatisでは、この判定を明示的に書く必要がある)。
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
        UUID orderId = order.orderId().value();
        OrderRow row = toOrderRow(order);

        // markPaid()/changeShippingAddress()等で確定済みOrderの状態が変わった後の再saveがここを通る。
        // order_linesはOrder確定後は不変(PressingSnapshotが凍結済み)なので、新規時のみ挿入する。
        boolean isNew = mapper.selectOrderById(orderId) == null;
        if (isNew) {
            mapper.insertOrder(row);
            for (OrderLine line : order.lines()) {
                mapper.insertOrderLine(toOrderLineRow(orderId, line));
            }
        } else {
            mapper.updateOrder(row);
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
        return toDomainList(mapper.selectOrdersByCustomerId(customerId.value()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findAll() {
        return toDomainList(mapper.selectAllOrders());
    }

    /**
     * OrderRowのリストをOrderのリストに組み立てる。findByCustomerId/findAllで共通の
     * 「N+1にしない」手順(該当する注文IDをまとめてIN句で1回のSELECTにしてJava側でグルーピング)。
     */
    private List<Order> toDomainList(List<OrderRow> orderRows) {
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
