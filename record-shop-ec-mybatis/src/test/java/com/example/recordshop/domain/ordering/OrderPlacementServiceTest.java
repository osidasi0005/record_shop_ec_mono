package com.example.recordshop.domain.ordering;

import com.example.recordshop.domain.catalog.Format;
import com.example.recordshop.domain.catalog.MediaType;
import com.example.recordshop.domain.catalog.Pressing;
import com.example.recordshop.domain.catalog.Release;
import com.example.recordshop.domain.catalog.ReleaseId;
import com.example.recordshop.domain.catalog.Speed;
import com.example.recordshop.domain.customer.CustomerId;
import com.example.recordshop.domain.inventory.ConditionType;
import com.example.recordshop.domain.inventory.GoldmineGrade;
import com.example.recordshop.domain.inventory.Listing;
import com.example.recordshop.domain.inventory.ListingId;
import com.example.recordshop.domain.inventory.ListingStatus;
import com.example.recordshop.domain.shared.Address;
import com.example.recordshop.domain.shared.InvariantViolationException;
import com.example.recordshop.domain.shared.Money;
import com.example.recordshop.infrastructure.memory.InMemoryListingRepository;
import com.example.recordshop.infrastructure.memory.InMemoryOrderRepository;
import com.example.recordshop.infrastructure.memory.InMemoryReleaseRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderPlacementServiceTest {

    private final InMemoryReleaseRepository releaseRepository = new InMemoryReleaseRepository();
    private final InMemoryListingRepository listingRepository = new InMemoryListingRepository();
    private final InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
    private final OrderPlacementService service =
            new OrderPlacementService(listingRepository, releaseRepository, orderRepository);

    private Address address() {
        return new Address("山田 太郎", "150-0001", "東京都", "渋谷区", "1-2-3", "JP");
    }

    @Test
    void placeOrder_PressingSnapshotが確定時点の内容で複製される() {
        Release release = Release.register(ReleaseId.generate(), "Kind of Blue", "Miles Davis",
                Set.of("Jazz"), 1959, null);
        Pressing pressing = release.addPressing("Columbia", "CL 1355", "US", 1959, "XSM", false,
                Format.vinyl(MediaType.LP, Speed.RPM_33, 1), null);
        releaseRepository.save(release);

        Listing listing = Listing.usedCopy(ListingId.generate(), pressing.pressingId(), Money.jpy(28000),
                GoldmineGrade.VERY_GOOD_PLUS, GoldmineGrade.VERY_GOOD, null);
        listing.publish(Instant.now());
        listingRepository.save(listing);

        Cart cart = Cart.open(CartId.generate(), CustomerId.generate());
        cart.addLine(listing.listingId(), ConditionType.USED, 1, Instant.now());

        Order order = service.placeOrder(cart, address(), address(), Instant.now());

        assertEquals(1, order.lines().size());
        var snapshot = order.lines().get(0).pressingSnapshot();
        assertEquals("Kind of Blue", snapshot.releaseTitle());
        assertEquals("CL 1355", snapshot.catalogNumber());
        assertEquals(GoldmineGrade.VERY_GOOD_PLUS, snapshot.vinylGrade());
        assertEquals(ListingStatus.RESERVED, listing.status());
    }

    @Test
    void placeOrder_一部のListingが公開されていない場合は全体をロールバックする() {
        Release release = Release.register(ReleaseId.generate(), "Kind of Blue", "Miles Davis",
                Set.of("Jazz"), 1959, null);
        Pressing pressing = release.addPressing("Columbia", "CL 1355", "US", 1959, "XSM", false,
                Format.vinyl(MediaType.LP, Speed.RPM_33, 1), null);
        releaseRepository.save(release);

        Listing publishedListing = Listing.newCopy(ListingId.generate(), pressing.pressingId(),
                Money.jpy(4200), 3);
        publishedListing.publish(Instant.now());
        listingRepository.save(publishedListing);

        // まだ DRAFT のまま(publish していない)の Listing = reserve() で例外になる
        Listing draftListing = Listing.newCopy(ListingId.generate(), pressing.pressingId(), Money.jpy(4200), 1);
        listingRepository.save(draftListing);

        Cart cart = Cart.open(CartId.generate(), CustomerId.generate());
        cart.addLine(publishedListing.listingId(), ConditionType.NEW, 1, Instant.now());
        cart.addLine(draftListing.listingId(), ConditionType.NEW, 1, Instant.now());

        assertThrows(RuntimeException.class, () -> service.placeOrder(cart, address(), address(), Instant.now()));

        // publishedListing 側の予約はロールバックされ、在庫が元に戻っていること
        Listing reloaded = listingRepository.findById(publishedListing.listingId()).orElseThrow();
        assertEquals(3, reloaded.stockQuantity());
        assertEquals(ListingStatus.PUBLISHED, reloaded.status());
    }

    @Test
    void placeOrder_空のカートは拒否される() {
        Cart cart = Cart.open(CartId.generate(), CustomerId.generate());

        assertThrows(InvariantViolationException.class,
                () -> service.placeOrder(cart, address(), address(), Instant.now()));
    }
}
