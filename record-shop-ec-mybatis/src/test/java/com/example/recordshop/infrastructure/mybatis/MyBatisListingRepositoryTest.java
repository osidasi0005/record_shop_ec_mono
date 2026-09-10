package com.example.recordshop.infrastructure.mybatis;

import com.example.recordshop.domain.catalog.PressingId;
import com.example.recordshop.domain.inventory.GoldmineGrade;
import com.example.recordshop.domain.inventory.Listing;
import com.example.recordshop.domain.inventory.ListingId;
import com.example.recordshop.domain.inventory.ListingStatus;
import com.example.recordshop.domain.shared.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class MyBatisListingRepositoryTest {

    @Autowired
    private MyBatisListingRepository repository;

    @Test
    void save_findById_で保存したListingが同じ内容で復元できる() {
        Listing listing = Listing.usedCopy(ListingId.generate(), PressingId.generate(),
                Money.jpy(28000), GoldmineGrade.VERY_GOOD_PLUS, GoldmineGrade.VERY_GOOD, "見開きジャケット");
        listing.publish(Instant.now());

        repository.save(listing);
        Listing found = repository.findById(listing.listingId()).orElseThrow();

        assertThat(found.status()).isEqualTo(ListingStatus.PUBLISHED);
        // price_amountはNUMERIC(12,2)列なので "28000" が "28000.00" として返る(スケール違い)。
        // BigDecimal#equalsはスケールを区別するため、値の比較にはcompareToを使う。
        assertThat(found.price().amount()).isEqualByComparingTo(Money.jpy(28000).amount());
        assertThat(found.price().currency()).isEqualTo(Money.jpy(28000).currency());
        assertThat(found.vinylGrade()).isEqualTo(GoldmineGrade.VERY_GOOD_PLUS);
        assertThat(found.sellerNote()).isEqualTo("見開きジャケット");
    }

    @Test
    void 状態遷移を保存してfindByIdで読み直すと最新状態が反映されている() {
        Listing listing = Listing.newCopy(ListingId.generate(), PressingId.generate(), Money.jpy(4200), 3);
        repository.save(listing);

        Listing loaded = repository.findById(listing.listingId()).orElseThrow();
        loaded.publish(Instant.now());
        loaded.reserve(1);
        repository.save(loaded);

        Listing reloaded = repository.findById(listing.listingId()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(ListingStatus.PUBLISHED);
        assertThat(reloaded.stockQuantity()).isEqualTo(2);
    }
}
