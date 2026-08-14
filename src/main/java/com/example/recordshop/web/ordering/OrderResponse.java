package com.example.recordshop.web.ordering;

import com.example.recordshop.domain.ordering.Order;
import com.example.recordshop.domain.ordering.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

public record OrderResponse(
        String orderId,
        String customerId,
        List<OrderLineResponse> lines,
        BigDecimal totalAmount,
        String totalCurrency,
        OrderStatus status
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.orderId().toString(),
                order.customerId().toString(),
                order.lines().stream().map(OrderLineResponse::from).toList(),
                order.totalAmount().amount(),
                order.totalAmount().currency().getCurrencyCode(),
                order.status()
        );
    }
}
