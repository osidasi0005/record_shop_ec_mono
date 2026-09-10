package com.example.recordshop.infrastructure.memory;

import com.example.recordshop.domain.catalog.PressingId;
import com.example.recordshop.domain.inventory.Listing;
import com.example.recordshop.domain.inventory.ListingId;
import com.example.recordshop.domain.inventory.ListingRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryListingRepository implements ListingRepository {

    private final Map<ListingId, Listing> store = new LinkedHashMap<>();

    @Override
    public void save(Listing listing) {
        store.put(listing.listingId(), listing);
    }

    @Override
    public Optional<Listing> findById(ListingId listingId) {
        return Optional.ofNullable(store.get(listingId));
    }

    @Override
    public List<Listing> findByPressingId(PressingId pressingId) {
        return store.values().stream()
                .filter(listing -> listing.pressingId().equals(pressingId))
                .toList();
    }
}
