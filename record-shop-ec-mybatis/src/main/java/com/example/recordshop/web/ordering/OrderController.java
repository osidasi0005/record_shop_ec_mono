package com.example.recordshop.web.ordering;

import com.example.recordshop.domain.ordering.Cart;
import com.example.recordshop.domain.ordering.CartId;
import com.example.recordshop.domain.customer.CustomerId;
import com.example.recordshop.domain.ordering.Order;
import com.example.recordshop.domain.ordering.OrderId;
import com.example.recordshop.domain.ordering.OrderPlacementService;
import com.example.recordshop.domain.ordering.OrderRepository;
import com.example.recordshop.domain.inventory.ListingId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Ordering コンテキストの Order に対する最小限の REST API。
 *
 * <p>「歩く骨格」の第三歩として、チェックアウト(注文確定)と注文取得のみを実装している。
 * Cart は永続化しない設計のため、リクエストボディそのものが「カートの中身」を運ぶ。
 */
@RestController
public class OrderController {

    private final OrderPlacementService orderPlacementService;
    private final OrderRepository orderRepository;

    public OrderController(OrderPlacementService orderPlacementService, OrderRepository orderRepository) {
        this.orderPlacementService = orderPlacementService;
        this.orderRepository = orderRepository;
    }

    @PostMapping("/api/checkout")
    @Transactional
    public ResponseEntity<OrderResponse> checkout(@RequestBody CheckoutRequest request) {
        CustomerId customerId = request.customerId() != null
                ? CustomerId.of(request.customerId())
                : CustomerId.generate();

        Cart cart = Cart.open(CartId.generate(), customerId);
        Instant now = Instant.now();
        for (CheckoutLineRequest line : request.lines()) {
            cart.addLine(ListingId.of(line.listingId()), line.conditionType(), line.quantity(), now);
        }

        Order order = orderPlacementService.placeOrder(
                cart,
                request.shippingAddress().toDomain(),
                request.billingAddress().toDomain(),
                now
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @GetMapping("/api/orders/{orderId}")
    public OrderResponse findById(@PathVariable String orderId) {
        Order order = orderRepository.findById(OrderId.of(orderId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + orderId));
        return OrderResponse.from(order);
    }
}
