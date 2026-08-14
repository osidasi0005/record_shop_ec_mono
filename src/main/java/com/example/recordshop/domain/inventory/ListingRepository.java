package com.example.recordshop.domain.inventory;

import com.example.recordshop.domain.catalog.PressingId;

import java.util.List;
import java.util.Optional;

/**
 * Listing 集約の永続化ポート(インターフェースのみ)。
 */
public interface ListingRepository {

    void save(Listing listing);

    Optional<Listing> findById(ListingId listingId);

    List<Listing> findByPressingId(PressingId pressingId);
}
