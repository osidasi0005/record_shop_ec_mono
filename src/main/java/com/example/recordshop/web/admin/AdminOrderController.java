package com.example.recordshop.web.admin;

import com.example.recordshop.domain.ordering.Order;
import com.example.recordshop.domain.ordering.OrderId;
import com.example.recordshop.domain.ordering.OrderRepository;
import com.example.recordshop.domain.shared.IllegalStateTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 出品者向け管理画面: 注文の一覧・入金確認・出荷先確認(GET/POST /admin/orders/**)。
 * {@code SecurityConfig} により ROLE_ADMIN のみアクセス可能。
 */
@Controller
@RequestMapping("/admin/orders")
public class AdminOrderController {

    private final OrderRepository orderRepository;

    public AdminOrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("orders", orderRepository.findAll());
        return "admin/orders/list";
    }

    @GetMapping("/{orderId}")
    public String detail(@PathVariable String orderId, Model model) {
        model.addAttribute("order", findOrThrow(orderId));
        return "admin/orders/detail";
    }

    /** 入金確認: 顧客からの入金を確認した後、PENDING -> PAID に遷移させる。 */
    @PostMapping("/{orderId}/mark-paid")
    public String markPaid(@PathVariable String orderId, RedirectAttributes redirectAttributes) {
        return transitionAndRedirect(orderId, Order::markPaid, "入金を確認しました", redirectAttributes);
    }

    /** 出荷: 出荷先住所を確認して発送した後、PAID -> SHIPPED に遷移させる。 */
    @PostMapping("/{orderId}/mark-shipped")
    public String markShipped(@PathVariable String orderId, RedirectAttributes redirectAttributes) {
        return transitionAndRedirect(orderId, Order::markShipped, "発送済みにしました", redirectAttributes);
    }

    /** 配達完了の記録: SHIPPED -> DELIVERED に遷移させる。 */
    @PostMapping("/{orderId}/mark-delivered")
    public String markDelivered(@PathVariable String orderId, RedirectAttributes redirectAttributes) {
        return transitionAndRedirect(orderId, Order::markDelivered, "配達完了にしました", redirectAttributes);
    }

    /** 注文キャンセル: PENDING/PAID -> CANCELLED に遷移させる。 */
    @PostMapping("/{orderId}/cancel")
    public String cancel(@PathVariable String orderId, RedirectAttributes redirectAttributes) {
        return transitionAndRedirect(orderId, Order::cancel, "注文をキャンセルしました", redirectAttributes);
    }

    private String transitionAndRedirect(String orderId, java.util.function.Consumer<Order> transition,
                                          String successMessage, RedirectAttributes redirectAttributes) {
        Order order = findOrThrow(orderId);
        try {
            transition.accept(order);
            orderRepository.save(order);
            redirectAttributes.addFlashAttribute("notice", successMessage);
        } catch (IllegalStateTransitionException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/orders/" + orderId;
    }

    private Order findOrThrow(String orderId) {
        return orderRepository.findById(OrderId.of(orderId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + orderId));
    }
}
