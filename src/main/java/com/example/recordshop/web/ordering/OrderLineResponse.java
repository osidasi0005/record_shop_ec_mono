package com.example.recordshop.web.ordering;

import com.example.recordshop.domain.ordering.OrderLine;

import java.math.BigDecimal;

public record OrderLineResponse(
        String listingId,
        String releaseTitle,
        String artistName,
        String catalogNumber,
        BigDecimal unitPriceAmount,
        String unitPriceCurrency,
        int quantity
) {
    public static OrderLineResponse from(OrderLine line) {
        return new OrderLineResponse(
                line.listingId().toString(),
                line.pressingSnapshot().releaseTitle(),
                line.pressingSnapshot().artistName(),
                line.pressingSnapshot().catalogNumber(),
                line.unitPrice().amount(),
                line.unitPrice().currency().getCurrencyCode(),
                line.quantity()
        );
    }
}
