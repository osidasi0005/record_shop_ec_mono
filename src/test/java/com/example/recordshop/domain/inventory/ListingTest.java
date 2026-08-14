package com.example.recordshop.domain.inventory;

import com.example.recordshop.domain.catalog.PressingId;
import com.example.recordshop.domain.shared.IllegalStateTransitionException;
import com.example.recordshop.domain.shared.InvariantViolationException;
import com.example.recordshop.domain.shared.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListingTest {

    @Test
    void used_publish後にreserveするとRESERVEDになる() {
        Listing listing = Listing.usedCopy(ListingId.generate(), PressingId.generate(),
                Money.jpy(28000), GoldmineGrade.VERY_GOOD_PLUS, GoldmineGrade.VERY_GOOD, null);
        listing.publish(Instant.now());

        listing.reserve(1);

        assertEquals(ListingStatus.RESERVED, listing.status());
    }

    @Test
    void used_数量2で予約しようとすると拒否される() {
        Listing listing = Listing.usedCopy(ListingId.generate(), PressingId.generate(),
                Money.jpy(28000), GoldmineGrade.MINT, GoldmineGrade.MINT, null);
        listing.publish(Instant.now());

        assertThrows(InvariantViolationException.class, () -> listing.reserve(2));
    }

    @Test
    void used_Soldになったら再度予約や公開に戻せない() {
        Listing listing = Listing.usedCopy(ListingId.generate(), PressingId.generate(),
                Money.jpy(28000), GoldmineGrade.MINT, GoldmineGrade.MINT, null);
        listing.publish(Instant.now());
        listing.reserve(1);
        listing.confirmSale(1, Instant.now());

        assertEquals(ListingStatus.SOLD, listing.status());
        assertThrows(IllegalStateTransitionException.class, () -> listing.reserve(1));
        assertThrows(IllegalStateTransitionException.class, () -> listing.cancelReservation(1));
        assertThrows(IllegalStateTransitionException.class, () -> listing.publish(Instant.now()));
    }

    @Test
    void new_在庫を超える予約は拒否される() {
        Listing listing = Listing.newCopy(ListingId.generate(), PressingId.generate(), Money.jpy(4200), 3);
        listing.publish(Instant.now());

        assertThrows(InvariantViolationException.class, () -> listing.reserve(4));
    }

    @Test
    void new_在庫が0になると自動的にOUT_OF_STOCKになる() {
        Listing listing = Listing.newCopy(ListingId.generate(), PressingId.generate(), Money.jpy(4200), 2);
        listing.publish(Instant.now());

        listing.reserve(2);

        assertEquals(0, listing.stockQuantity());
        assertEquals(ListingStatus.OUT_OF_STOCK, listing.status());
    }

    @Test
    void new_キャンセルされると在庫が戻りステータスもPUBLISHEDに戻る() {
        Listing listing = Listing.newCopy(ListingId.generate(), PressingId.generate(), Money.jpy(4200), 1);
        listing.publish(Instant.now());
        listing.reserve(1);
        assertEquals(ListingStatus.OUT_OF_STOCK, listing.status());

        listing.cancelReservation(1);

        assertEquals(1, listing.stockQuantity());
        assertEquals(ListingStatus.PUBLISHED, listing.status());
    }
}
