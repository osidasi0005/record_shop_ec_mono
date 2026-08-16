package com.example.recordshop.web.ordering;

import com.example.recordshop.domain.catalog.Format;
import com.example.recordshop.domain.catalog.MediaType;
import com.example.recordshop.domain.catalog.Pressing;
import com.example.recordshop.domain.catalog.PressingId;
import com.example.recordshop.domain.catalog.Release;
import com.example.recordshop.domain.catalog.ReleaseId;
import com.example.recordshop.domain.catalog.Speed;
import com.example.recordshop.domain.customer.CustomerId;
import com.example.recordshop.domain.inventory.ConditionType;
import com.example.recordshop.domain.inventory.Listing;
import com.example.recordshop.domain.inventory.ListingId;
import com.example.recordshop.domain.inventory.ListingRepository;
import com.example.recordshop.domain.ordering.Cart;
import com.example.recordshop.domain.ordering.CartId;
import com.example.recordshop.domain.ordering.Order;
import com.example.recordshop.domain.ordering.OrderPlacementService;
import com.example.recordshop.domain.payment.PaymentCaptureService;
import com.example.recordshop.domain.payment.PaymentStatus;
import com.example.recordshop.domain.shared.Address;
import com.example.recordshop.domain.shared.Money;
import com.example.recordshop.infrastructure.memory.InMemoryListingRepository;
import com.example.recordshop.infrastructure.memory.InMemoryOrderRepository;
import com.example.recordshop.infrastructure.memory.InMemoryPaymentRepository;
import com.example.recordshop.infrastructure.memory.InMemoryReleaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * チェックアウトの楽観ロック競合リトライの検証。
 *
 * <p>トランザクションそのものはインメモリ実装に対しては意味を持たないため、
 * ここでは「競合したら何回まで引き直すか」というリトライの振る舞いだけを対象にする。
 */
class CheckoutServiceTest {

    /** commit/rollbackが何もしないトランザクションマネージャ(境界の有無ではなくリトライ回数を見たいため)。 */
    private static final PlatformTransactionManager NOOP_TX_MANAGER = new PlatformTransactionManager() {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    };

    /** 最初の {@code failures} 回だけ save() で楽観ロック競合を起こすリポジトリ。 */
    private static final class FlakyListingRepository implements ListingRepository {
        private final ListingRepository delegate;
        private int remainingFailures;
        private int saveAttempts;

        FlakyListingRepository(ListingRepository delegate, int failures) {
            this.delegate = delegate;
            this.remainingFailures = failures;
        }

        @Override
        public void save(Listing listing) {
            saveAttempts++;
            if (remainingFailures > 0) {
                remainingFailures--;
                throw new OptimisticLockingFailureException("競合");
            }
            delegate.save(listing);
        }

        @Override
        public Optional<Listing> findById(ListingId listingId) {
            return delegate.findById(listingId);
        }

        @Override
        public List<Listing> findByPressingId(PressingId pressingId) {
            return delegate.findByPressingId(pressingId);
        }
    }

    private final InMemoryReleaseRepository releaseRepository = new InMemoryReleaseRepository();
    private final InMemoryListingRepository listingRepository = new InMemoryListingRepository();
    private final InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
    private final InMemoryPaymentRepository paymentRepository = new InMemoryPaymentRepository();

    private final Address address =
            new Address("山田 太郎", "150-0001", "東京都", "渋谷区", "1-2-3", "JP");

    private ListingId seedPublishedListing(int stock) {
        Release release = Release.register(ReleaseId.generate(), "Kind of Blue", "Miles Davis",
                Set.of("Jazz"), 1959, null);
        Pressing pressing = release.addPressing("Columbia", "CL 1355", "US", 1959, "XSM", false,
                Format.vinyl(MediaType.LP, Speed.RPM_33, 1), null);
        releaseRepository.save(release);

        Listing listing = Listing.newCopy(ListingId.generate(), pressing.pressingId(),
                new Money(new BigDecimal("4200"), Currency.getInstance("JPY")), stock);
        listing.publish(Instant.now());
        listingRepository.save(listing);
        return listing.listingId();
    }

    private Cart cartWith(ListingId listingId) {
        Cart cart = Cart.open(CartId.generate(), CustomerId.generate());
        cart.addLine(listingId, ConditionType.NEW, 1, Instant.now());
        return cart;
    }

    private CheckoutService checkoutService(ListingRepository listings) {
        return new CheckoutService(
                new OrderPlacementService(listings, releaseRepository, orderRepository),
                new PaymentCaptureService(orderRepository, paymentRepository),
                NOOP_TX_MANAGER);
    }

    @Test
    void checkout_競合しなければ注文確定と決済Captureが行われる() {
        ListingId listingId = seedPublishedListing(5);

        Order order = checkoutService(listingRepository).checkout(cartWith(listingId), address, address);

        assertThat(order.status()).isNotNull();
        assertThat(paymentRepository.findByOrderId(order.orderId()))
                .singleElement()
                .extracting(payment -> payment.status())
                .isEqualTo(PaymentStatus.CAPTURED);
    }

    @Test
    void checkout_楽観ロック競合が起きても引き直して成功する() {
        // 在庫が十分あるのに他の購入者と同時実行しただけで失敗させない(ST-CONC-002)
        ListingId listingId = seedPublishedListing(5);
        FlakyListingRepository flaky = new FlakyListingRepository(listingRepository, 1);

        Order order = checkoutService(flaky).checkout(cartWith(listingId), address, address);

        assertThat(order).isNotNull();
        assertThat(flaky.saveAttempts).isEqualTo(2);
    }

    @Test
    void checkout_リトライ上限まで競合し続けたら例外を伝播する() {
        // 本当に売り切れているケースは、呼び出し元が案内メッセージへ変換する(ST-CONC-001)
        ListingId listingId = seedPublishedListing(5);
        FlakyListingRepository flaky = new FlakyListingRepository(listingRepository, Integer.MAX_VALUE);

        CheckoutService service = checkoutService(flaky);
        Cart cart = cartWith(listingId);

        assertThatThrownBy(() -> service.checkout(cart, address, address))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(flaky.saveAttempts).isEqualTo(3);
    }
}
