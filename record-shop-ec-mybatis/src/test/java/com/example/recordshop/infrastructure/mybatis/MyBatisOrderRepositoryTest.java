package com.example.recordshop.infrastructure.mybatis;

import com.example.recordshop.domain.catalog.Format;
import com.example.recordshop.domain.catalog.MediaType;
import com.example.recordshop.domain.catalog.Speed;
import com.example.recordshop.domain.customer.CustomerId;
import com.example.recordshop.domain.inventory.ConditionType;
import com.example.recordshop.domain.inventory.ListingId;
import com.example.recordshop.domain.ordering.Order;
import com.example.recordshop.domain.ordering.OrderId;
import com.example.recordshop.domain.ordering.OrderLine;
import com.example.recordshop.domain.ordering.PressingSnapshot;
import com.example.recordshop.domain.shared.Address;
import com.example.recordshop.domain.shared.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MyBatisアダプタの結合テスト。{@link MyBatisOrderRepository#findByCustomerId}が
 * 複数注文のOrderLineを取り違えなくグルーピングできているかを重点的に確認する
 * (Release集約のfindAll()と同じ「N+1を避けつつ正しく組み立てる」設計の検証)。
 */
@SpringBootTest
@Transactional
class MyBatisOrderRepositoryTest {

    @Autowired
    private MyBatisOrderRepository repository;

    private Address address(String city) {
        return new Address("山田 太郎", "150-0001", "東京都", city, "1-2-3", "JP");
    }

    private OrderLine line(String title, Money unitPrice, int quantity) {
        PressingSnapshot snapshot = new PressingSnapshot(
                title, "Miles Davis", "Columbia", "CL 1355", "US", 1959,
                Format.vinyl(MediaType.LP, Speed.RPM_33, 1),
                ConditionType.NEW, null, null);
        return new OrderLine(ListingId.generate(), snapshot, unitPrice, quantity);
    }

    @Test
    void save_findById_で保存したOrderが住所_明細ともに同じ内容で復元できる() {
        CustomerId customerId = CustomerId.generate();
        Order order = Order.place(OrderId.generate(), customerId,
                List.of(line("Kind of Blue", Money.jpy(4200), 2)),
                address("渋谷区"), address("新宿区"), Instant.now());

        repository.save(order);
        Order found = repository.findById(order.orderId()).orElseThrow();

        assertThat(found.customerId()).isEqualTo(customerId);
        assertThat(found.shippingAddress().city()).isEqualTo("渋谷区");
        assertThat(found.billingAddress().city()).isEqualTo("新宿区");
        assertThat(found.lines()).hasSize(1);
        assertThat(found.lines().get(0).pressingSnapshot().releaseTitle()).isEqualTo("Kind of Blue");
        assertThat(found.lines().get(0).quantity()).isEqualTo(2);
    }

    @Test
    void findByCustomerId_で複数注文のOrderLineが取り違えなくグルーピングされる() {
        CustomerId customerId = CustomerId.generate();
        Order order1 = Order.place(OrderId.generate(), customerId,
                List.of(line("Kind of Blue", Money.jpy(4200), 1)),
                address("渋谷区"), address("渋谷区"), Instant.now());
        Order order2 = Order.place(OrderId.generate(), customerId,
                List.of(line("A Love Supreme", Money.jpy(3800), 1), line("Blue Train", Money.jpy(3500), 2)),
                address("大阪市"), address("大阪市"), Instant.now());
        repository.save(order1);
        repository.save(order2);

        List<Order> found = repository.findByCustomerId(customerId);

        assertThat(found).hasSize(2);
        Order foundOrder2 = found.stream()
                .filter(o -> o.orderId().equals(order2.orderId()))
                .findFirst().orElseThrow();
        // order1のlinesがorder2に混ざっていない(IN句一括取得後のグルーピングが正しい)ことを確認
        assertThat(foundOrder2.lines()).hasSize(2);
        assertThat(foundOrder2.lines())
                .extracting(l -> l.pressingSnapshot().releaseTitle())
                .containsExactlyInAnyOrder("A Love Supreme", "Blue Train");
    }

    @Test
    void findAll_で異なる顧客の注文も含めて全件_明細を取り違えなく取得できる() {
        Order order1 = Order.place(OrderId.generate(), CustomerId.generate(),
                List.of(line("Kind of Blue", Money.jpy(4200), 1)),
                address("渋谷区"), address("渋谷区"), Instant.now());
        Order order2 = Order.place(OrderId.generate(), CustomerId.generate(),
                List.of(line("A Love Supreme", Money.jpy(3800), 1), line("Blue Train", Money.jpy(3500), 2)),
                address("大阪市"), address("大阪市"), Instant.now());
        repository.save(order1);
        repository.save(order2);

        List<Order> found = repository.findAll();

        assertThat(found).extracting(Order::orderId)
                .contains(order1.orderId(), order2.orderId());
        Order foundOrder2 = found.stream()
                .filter(o -> o.orderId().equals(order2.orderId()))
                .findFirst().orElseThrow();
        assertThat(foundOrder2.lines()).hasSize(2);
        assertThat(foundOrder2.lines())
                .extracting(l -> l.pressingSnapshot().releaseTitle())
                .containsExactlyInAnyOrder("A Love Supreme", "Blue Train");
    }
}
