package com.example.recordshop.web.ordering;

import com.example.recordshop.domain.catalog.ReleaseRepository;
import com.example.recordshop.domain.inventory.ListingRepository;
import com.example.recordshop.domain.ordering.Cart;
import com.example.recordshop.domain.ordering.Order;
import com.example.recordshop.domain.ordering.OrderPlacementService;
import com.example.recordshop.domain.payment.PaymentCaptureService;
import com.example.recordshop.domain.payment.PaymentMethod;
import com.example.recordshop.domain.shared.Address;
import com.example.recordshop.domain.shared.IllegalStateTransitionException;
import com.example.recordshop.domain.shared.InvariantViolationException;
import com.example.recordshop.infrastructure.security.CustomerUserDetails;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.util.List;

/**
 * チェックアウト画面(GET/POST /checkout)。
 *
 * <p>「歩く骨格」フェーズの簡易実装として、配送先・請求先を単一の住所フォームで受け取り、
 * 注文確定に続けて即座に決済Captureまで行う(実際の決済手段選択・カード情報入力画面は持たない ―
 * {@link com.example.recordshop.domain.payment.Payment} 自体が「Captureは常に即成功」という
 * 擬似実装のため、チェックアウト=購入完了という単純なUXにしている)。
 *
 * <p>楽観ロック競合の捕捉は、JPA版の{@code ObjectOptimisticLockingFailureException}ではなく
 * その基底クラス{@link OptimisticLockingFailureException}を使う({@link com.example.recordshop.web.ApiExceptionHandler}
 * と同じ理由)。
 */
@Controller
public class CheckoutController {

    private final ListingRepository listingRepository;
    private final ReleaseRepository releaseRepository;
    private final OrderPlacementService orderPlacementService;
    private final PaymentCaptureService paymentCaptureService;

    public CheckoutController(ListingRepository listingRepository, ReleaseRepository releaseRepository,
                               OrderPlacementService orderPlacementService,
                               PaymentCaptureService paymentCaptureService) {
        this.listingRepository = listingRepository;
        this.releaseRepository = releaseRepository;
        this.orderPlacementService = orderPlacementService;
        this.paymentCaptureService = paymentCaptureService;
    }

    @GetMapping("/checkout")
    public String showForm(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Cart cart = (Cart) session.getAttribute(CartController.SESSION_KEY);
        if (cart == null || cart.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "カートが空です");
            return "redirect:/cart";
        }

        List<CartLineView> lines = CartViewAssembler.assemble(cart, listingRepository, releaseRepository);
        model.addAttribute("lines", lines);
        model.addAttribute("total", CartViewAssembler.total(lines).orElse(null));
        if (!model.containsAttribute("checkoutForm")) {
            model.addAttribute("checkoutForm", new CheckoutForm());
        }
        return "checkout/form";
    }

    @PostMapping("/checkout")
    @Transactional
    public String submit(@AuthenticationPrincipal CustomerUserDetails principal, HttpSession session,
                          @ModelAttribute CheckoutForm form, RedirectAttributes redirectAttributes) {
        Cart cart = (Cart) session.getAttribute(CartController.SESSION_KEY);
        if (cart == null || cart.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "カートが空です");
            return "redirect:/cart";
        }

        Address address = new Address(form.getRecipientName(), form.getPostalCode(), form.getPrefecture(),
                form.getCity(), form.getAddressLine(), form.getCountry());
        Instant now = Instant.now();

        try {
            Order order = orderPlacementService.placeOrder(cart, address, address, now);
            paymentCaptureService.capturePayment(order.orderId(), PaymentMethod.CREDIT_CARD, now);

            session.removeAttribute(CartController.SESSION_KEY);
            return "redirect:/orders/" + order.orderId();
        } catch (OptimisticLockingFailureException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "他の注文と同時に処理されたため確定できませんでした。もう一度お試しください。");
            return "redirect:/cart";
        } catch (InvariantViolationException | IllegalStateTransitionException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/cart";
        }
    }
}
