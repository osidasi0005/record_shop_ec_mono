package com.example.recordshop.web.ordering;

import com.example.recordshop.domain.catalog.Pressing;
import com.example.recordshop.domain.catalog.Release;
import com.example.recordshop.domain.catalog.ReleaseRepository;
import com.example.recordshop.domain.inventory.Listing;
import com.example.recordshop.domain.inventory.ListingRepository;
import com.example.recordshop.domain.ordering.Cart;
import com.example.recordshop.domain.ordering.CartLine;
import com.example.recordshop.domain.shared.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cart(listingId+quantityのみ保持)をカタログ情報と合成して画面表示用の {@link CartLineView} に組み立てる。
 * カート画面・チェックアウト画面の両方から使う共通ロジック。
 */
final class CartViewAssembler {

    private CartViewAssembler() {
    }

    static List<CartLineView> assemble(Cart cart, ListingRepository listingRepository,
                                        ReleaseRepository releaseRepository) {
        List<CartLineView> lines = new ArrayList<>();
        for (CartLine line : cart.lines()) {
            toView(line, listingRepository, releaseRepository).ifPresent(lines::add);
        }
        return lines;
    }

    static Optional<Money> total(List<CartLineView> lines) {
        Money total = null;
        for (CartLineView line : lines) {
            total = total == null ? line.lineTotal() : total.add(line.lineTotal());
        }
        return Optional.ofNullable(total);
    }

    private static Optional<CartLineView> toView(CartLine line, ListingRepository listingRepository,
                                                   ReleaseRepository releaseRepository) {
        Listing listing = listingRepository.findById(line.listingId()).orElse(null);
        if (listing == null) {
            return Optional.empty();
        }
        Release release = releaseRepository.findByPressingId(listing.pressingId()).orElse(null);
        if (release == null) {
            return Optional.empty();
        }
        Pressing pressing = release.findPressing(listing.pressingId()).orElse(null);
        if (pressing == null) {
            return Optional.empty();
        }

        Money lineTotal = listing.price().multiply(line.quantity());
        return Optional.of(new CartLineView(
                line.listingId().toString(),
                release.title(),
                release.artistName(),
                pressing.catalogNumber(),
                line.conditionType(),
                listing.price(),
                line.quantity(),
                lineTotal
        ));
    }
}
