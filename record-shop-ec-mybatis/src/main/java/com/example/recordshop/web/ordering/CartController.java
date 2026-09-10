package com.example.recordshop.web.ordering;

import com.example.recordshop.domain.catalog.ReleaseRepository;
import com.example.recordshop.domain.inventory.Listing;
import com.example.recordshop.domain.inventory.ListingId;
import com.example.recordshop.domain.inventory.ListingRepository;
import com.example.recordshop.domain.ordering.Cart;
import com.example.recordshop.domain.ordering.CartId;
import com.example.recordshop.domain.shared.MalformedIdentifierException;
import com.example.recordshop.infrastructure.security.CustomerUserDetails;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * カート画面(GET/POST /cart/**)。
 *
 * <p>Cart 集約はDBに永続化せず、{@link HttpSession} 上に保持する(ログイン必須の画面のため、
 * ログアウト・セッション切れでカートが消える点は「歩く骨格」フェーズの簡易実装として許容する)。
 */
@Controller
public class CartController {

    static final String SESSION_KEY = "cart";

    private final ListingRepository listingRepository;
    private final ReleaseRepository releaseRepository;

    public CartController(ListingRepository listingRepository, ReleaseRepository releaseRepository) {
        this.listingRepository = listingRepository;
        this.releaseRepository = releaseRepository;
    }

    @GetMapping("/cart")
    public String view(@AuthenticationPrincipal CustomerUserDetails principal, HttpSession session, Model model) {
        Cart cart = currentCart(principal, session);
        List<CartLineView> lines = CartViewAssembler.assemble(cart, listingRepository, releaseRepository);

        model.addAttribute("lines", lines);
        model.addAttribute("total", CartViewAssembler.total(lines).orElse(null));
        return "cart/view";
    }

    @PostMapping("/cart/add")
    public String add(@AuthenticationPrincipal CustomerUserDetails principal, HttpSession session,
                       @RequestParam String listingId, @RequestParam(defaultValue = "1") int quantity,
                       RedirectAttributes redirectAttributes) {
        Cart cart = currentCart(principal, session);

        // ID形式が不正な場合も「見つからなかった」と同じ扱いにする(利用者にとっては区別に意味がない)
        Listing listing = parseListingId(listingId)
                .flatMap(listingRepository::findById)
                .orElse(null);
        if (listing == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "指定された商品が見つかりませんでした");
            return "redirect:/catalog";
        }

        cart.addLine(listing.listingId(), listing.conditionType(), Math.max(quantity, 1), Instant.now());
        return "redirect:/cart";
    }

    @PostMapping("/cart/remove")
    public String remove(@AuthenticationPrincipal CustomerUserDetails principal, HttpSession session,
                          @RequestParam String listingId) {
        Cart cart = currentCart(principal, session);
        parseListingId(listingId).ifPresent(cart::removeLine);
        return "redirect:/cart";
    }

    /** 不正な形式のlistingIdを例外にせず「該当なし」として扱う。 */
    private Optional<ListingId> parseListingId(String listingId) {
        try {
            return Optional.of(ListingId.of(listingId));
        } catch (MalformedIdentifierException e) {
            return Optional.empty();
        }
    }

    /** セッションに保持中の Cart を返す。無ければログイン中の顧客用に新規作成する。 */
    private Cart currentCart(CustomerUserDetails principal, HttpSession session) {
        Cart cart = (Cart) session.getAttribute(SESSION_KEY);
        if (cart == null) {
            cart = Cart.open(CartId.generate(), principal.customerId());
            session.setAttribute(SESSION_KEY, cart);
        }
        return cart;
    }
}
