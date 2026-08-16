package com.example.recordshop.web.ordering;

import com.example.recordshop.domain.ordering.Order;
import com.example.recordshop.domain.ordering.OrderId;
import com.example.recordshop.domain.ordering.OrderRepository;
import com.example.recordshop.infrastructure.security.CustomerUserDetails;
import com.example.recordshop.web.PathIds;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * 注文履歴画面(GET /orders, /orders/{orderId})。ログイン必須(SecurityConfigのanyRequest().authenticated()経由)。
 */
@Controller
public class OrderHistoryController {

    private final OrderRepository orderRepository;

    public OrderHistoryController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping("/orders")
    public String list(@AuthenticationPrincipal CustomerUserDetails principal, Model model) {
        model.addAttribute("orders", orderRepository.findByCustomerId(principal.customerId()));
        return "orders/list";
    }

    @GetMapping("/orders/{orderId}")
    public String detail(@AuthenticationPrincipal CustomerUserDetails principal,
                          @PathVariable String orderId, Model model) {
        Order order = orderRepository.findById(PathIds.parse(orderId, OrderId::of, "Order"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + orderId));

        // 他人の注文詳細URLを直接叩かれても中身を見せない(所有者チェック)
        if (!order.customerId().equals(principal.customerId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + orderId);
        }

        model.addAttribute("order", order);
        return "orders/detail";
    }
}
