package com.example.recordshop.web.inventory;

import com.example.recordshop.domain.catalog.PressingId;
import com.example.recordshop.domain.catalog.ReleaseRepository;
import com.example.recordshop.domain.inventory.ConditionType;
import com.example.recordshop.domain.inventory.Listing;
import com.example.recordshop.domain.inventory.ListingId;
import com.example.recordshop.domain.inventory.ListingRepository;
import com.example.recordshop.domain.shared.Money;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Currency;

/**
 * Inventory コンテキストの Listing に対する最小限の REST API。
 *
 * <p>「歩く骨格」の第二歩として、出品(DRAFT作成)と公開(PUBLISHED化)のみを実装している。
 * 予約(reserve)・販売確定(confirmSale)は {@link com.example.recordshop.web.ordering.OrderController}
 * 経由の注文確定フローの中で {@code OrderPlacementService} が行う。
 */
@RestController
@RequestMapping("/api/listings")
public class ListingController {

    private final ListingRepository listingRepository;
    private final ReleaseRepository releaseRepository;

    public ListingController(ListingRepository listingRepository, ReleaseRepository releaseRepository) {
        this.listingRepository = listingRepository;
        this.releaseRepository = releaseRepository;
    }

    @PostMapping
    public ResponseEntity<ListingResponse> create(@RequestBody CreateListingRequest request) {
        PressingId pressingId = PressingId.of(request.pressingId());
        releaseRepository.findByPressingId(pressingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Pressing not found: " + request.pressingId()));

        Currency currency = request.priceCurrency() != null
                ? Currency.getInstance(request.priceCurrency())
                : Money.JPY;
        Money price = new Money(request.priceAmount(), currency);

        Listing listing;
        if (request.conditionType() == ConditionType.USED) {
            listing = Listing.usedCopy(ListingId.generate(), pressingId, price,
                    request.vinylGrade(), request.sleeveGrade(), request.sellerNote());
        } else {
            int initialStock = request.initialStock() != null ? request.initialStock() : 1;
            listing = Listing.newCopy(ListingId.generate(), pressingId, price, initialStock);
        }

        listingRepository.save(listing);
        return ResponseEntity.status(HttpStatus.CREATED).body(ListingResponse.from(listing));
    }

    @PostMapping("/{listingId}/publish")
    public ListingResponse publish(@PathVariable String listingId) {
        Listing listing = findOrThrow(listingId);
        listing.publish(Instant.now());
        listingRepository.save(listing);
        return ListingResponse.from(listing);
    }

    @GetMapping("/{listingId}")
    public ListingResponse findById(@PathVariable String listingId) {
        return ListingResponse.from(findOrThrow(listingId));
    }

    private Listing findOrThrow(String listingId) {
        return listingRepository.findById(ListingId.of(listingId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found: " + listingId));
    }
}
