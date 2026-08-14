package com.example.recordshop.web.inventory;

import com.example.recordshop.domain.inventory.ConditionType;
import com.example.recordshop.domain.inventory.GoldmineGrade;
import com.example.recordshop.domain.inventory.Listing;
import com.example.recordshop.domain.inventory.ListingStatus;

import java.math.BigDecimal;

public record ListingResponse(
        String listingId,
        String pressingId,
        ConditionType conditionType,
        BigDecimal priceAmount,
        String priceCurrency,
        ListingStatus status,
        Integer stockQuantity,
        GoldmineGrade vinylGrade,
        GoldmineGrade sleeveGrade,
        String sellerNote
) {
    public static ListingResponse from(Listing listing) {
        return new ListingResponse(
                listing.listingId().toString(),
                listing.pressingId().toString(),
                listing.conditionType(),
                listing.price().amount(),
                listing.price().currency().getCurrencyCode(),
                listing.status(),
                listing.stockQuantity(),
                listing.vinylGrade(),
                listing.sleeveGrade(),
                listing.sellerNote()
        );
    }
}
