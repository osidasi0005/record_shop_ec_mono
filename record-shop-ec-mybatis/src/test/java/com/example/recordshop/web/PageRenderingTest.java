package com.example.recordshop.web;

import com.example.recordshop.domain.catalog.Format;
import com.example.recordshop.domain.catalog.MediaType;
import com.example.recordshop.domain.catalog.Pressing;
import com.example.recordshop.domain.catalog.Release;
import com.example.recordshop.domain.catalog.ReleaseId;
import com.example.recordshop.domain.catalog.ReleaseRepository;
import com.example.recordshop.domain.catalog.Speed;
import com.example.recordshop.domain.customer.Customer;
import com.example.recordshop.domain.customer.CustomerId;
import com.example.recordshop.domain.customer.CustomerRepository;
import com.example.recordshop.domain.customer.Email;
import com.example.recordshop.domain.customer.EmailDeliveryException;
import com.example.recordshop.domain.customer.EmailSender;
import com.example.recordshop.domain.customer.EmailVerification;
import com.example.recordshop.domain.customer.EmailVerificationId;
import com.example.recordshop.domain.customer.EmailVerificationRepository;
import com.example.recordshop.domain.inventory.ConditionType;
import com.example.recordshop.domain.inventory.Listing;
import com.example.recordshop.domain.inventory.ListingId;
import com.example.recordshop.domain.inventory.ListingRepository;
import com.example.recordshop.domain.ordering.Cart;
import com.example.recordshop.domain.ordering.CartId;
import com.example.recordshop.domain.ordering.Order;
import com.example.recordshop.domain.ordering.OrderPlacementService;
import com.example.recordshop.domain.payment.PaymentCaptureService;
import com.example.recordshop.domain.payment.PaymentMethod;
import com.example.recordshop.domain.shared.Address;
import com.example.recordshop.domain.shared.Money;
import com.example.recordshop.infrastructure.security.CustomerUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 画面(Thymeleaf)を実際にレンダリングして検証する。
 *
 * <p>システムテストで見つかった不具合のうち、テンプレートとコントローラの噛み合わせに起因するもの
 * (エラー時のモデル属性不足による500、フラッシュメッセージの描画漏れ、決済状態の未表示)は
 * ドメイン層の単体テストでは検出できなかった。同種の再発を防ぐための回帰テスト。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PageRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReleaseRepository releaseRepository;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Autowired
    private OrderPlacementService orderPlacementService;

    @Autowired
    private PaymentCaptureService paymentCaptureService;

    /** 実際のSES送信を行わせない。送信失敗時の画面挙動もここから再現する。 */
    @MockitoBean
    private EmailSender emailSender;

    private Release seedRelease(String title) {
        Release release = Release.register(ReleaseId.generate(), title, "Test Artist", Set.of("Jazz"), 1990, null);
        release.addPressing("Test Label", "TL-" + System.nanoTime(), "JP", 1990, null, false,
                Format.vinyl(MediaType.LP, Speed.RPM_33, 1), null);
        releaseRepository.save(release);
        return release;
    }

    private Listing seedListing(Pressing pressing, boolean publish, int stock) {
        Listing listing = Listing.newCopy(ListingId.generate(), pressing.pressingId(),
                new Money(new BigDecimal("3500"), Currency.getInstance("JPY")), stock);
        if (publish) {
            listing.publish(Instant.now());
        }
        listingRepository.save(listing);
        return listing;
    }

    private CustomerUserDetails seedCustomer() {
        Customer customer = Customer.register(CustomerId.generate(),
                new Email("page-test-" + System.nanoTime() + "@example.com"),
                "$2a$10$abcdefghijklmnopqrstuv", "画面テスト太郎", Instant.now());
        customerRepository.save(customer);
        return new CustomerUserDetails(customer);
    }

    // --- 不具合#1: /catalog の公開状態フィルタ ---

    @Test
    void 商品一覧にはPUBLISHEDな出品を持つ作品だけが並ぶ() throws Exception {
        Release published = seedRelease("Published Album " + System.nanoTime());
        seedListing(published.pressings().get(0), true, 3);

        Release draftOnly = seedRelease("Draft Only Album " + System.nanoTime());
        seedListing(draftOnly.pressings().get(0), false, 3);

        mockMvc.perform(get("/catalog"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(published.title())))
                .andExpect(content().string(not(containsString(draftOnly.title()))));
    }

    // --- 不具合#2: 形式が不正なIDは404 ---

    @Test
    void 商品詳細にUUIDでないIDを渡すと404になる() throws Exception {
        mockMvc.perform(get("/catalog/999999")).andExpect(status().isNotFound());
    }

    @Test
    void 形式が正しい未存在UUIDも404になる() throws Exception {
        mockMvc.perform(get("/catalog/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    // --- 不具合#4: 確認コード画面のエラー時に500にならない ---

    @Test
    void 確認コードを間違えても画面が描画されエラーメッセージが表示される() throws Exception {
        Email email = new Email("confirm-test-" + System.nanoTime() + "@example.com");
        EmailVerification verification = EmailVerification.issue(EmailVerificationId.generate(), email,
                "$2a$10$abcdefghijklmnopqrstuv", "確認太郎", Instant.now(), Duration.ofMinutes(10));
        emailVerificationRepository.save(verification);

        String wrongCode = "000000".equals(verification.verificationCode()) ? "111111" : "000000";

        mockMvc.perform(post("/register/confirm").with(csrf())
                        .param("email", email.value())
                        .param("code", wrongCode))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("確認コードが正しくありません")));
    }

    // --- 不具合#3: 確認コードメールの送信に失敗しても500にしない ---

    @Test
    void 確認コードメールの送信に失敗したら登録画面にエラーメッセージを出す() throws Exception {
        willThrow(new EmailDeliveryException("送信失敗", new RuntimeException()))
                .given(emailSender).sendVerificationCode(any(), any(), any());

        mockMvc.perform(post("/register").with(csrf())
                        .param("email", "delivery-fail-" + System.nanoTime() + "@example.com")
                        .param("password", "Passw0rd!2024")
                        .param("displayName", "送信失敗太郎"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("確認コードのメールを送信できませんでした")));
    }

    // --- 不具合#5: カート追加失敗時のフラッシュメッセージが画面に出る ---

    @Test
    void 存在しない商品をカートに入れようとすると商品一覧にメッセージが表示される() throws Exception {
        CustomerUserDetails principal = seedCustomer();

        mockMvc.perform(post("/cart/add").with(user(principal)).with(csrf())
                        .param("listingId", "nonexistent-id")
                        .param("quantity", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/catalog"))
                .andExpect(flash().attributeExists("errorMessage"));

        // フラッシュ属性が設定されるだけでなく、遷移先のテンプレートで実際に描画されること
        mockMvc.perform(get("/catalog").with(user(principal))
                        .flashAttr("errorMessage", "指定された商品が見つかりませんでした"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("指定された商品が見つかりませんでした")));
    }

    // --- 不具合#8: 注文詳細に決済状態が出る ---

    @Test
    void 注文詳細に決済状態が表示される() throws Exception {
        CustomerUserDetails principal = seedCustomer();
        Release release = seedRelease("Payment Album " + System.nanoTime());
        Listing listing = seedListing(release.pressings().get(0), true, 3);

        Cart cart = Cart.open(CartId.generate(), principal.customerId());
        cart.addLine(listing.listingId(), ConditionType.NEW, 1, Instant.now());
        Address address = new Address("画面テスト太郎", "150-0001", "東京都", "渋谷区", "1-2-3", "JP");
        Order order = orderPlacementService.placeOrder(cart, address, address, Instant.now());
        paymentCaptureService.capturePayment(order.orderId(), PaymentMethod.CREDIT_CARD, Instant.now());

        mockMvc.perform(get("/orders/" + order.orderId()).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("お支払い状況")))
                .andExpect(content().string(containsString("CAPTURED")));
    }
}
