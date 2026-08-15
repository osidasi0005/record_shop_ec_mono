package com.example.recordshop.infrastructure.devdata;

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
import com.example.recordshop.domain.customer.PasswordHasher;
import com.example.recordshop.domain.inventory.ConditionType;
import com.example.recordshop.domain.inventory.GoldmineGrade;
import com.example.recordshop.domain.inventory.Listing;
import com.example.recordshop.domain.inventory.ListingId;
import com.example.recordshop.domain.inventory.ListingRepository;
import com.example.recordshop.domain.inventory.ListingStatus;
import com.example.recordshop.domain.ordering.Cart;
import com.example.recordshop.domain.ordering.CartId;
import com.example.recordshop.domain.ordering.Order;
import com.example.recordshop.domain.ordering.OrderPlacementService;
import com.example.recordshop.domain.ordering.OrderRepository;
import com.example.recordshop.domain.payment.Payment;
import com.example.recordshop.domain.payment.PaymentCaptureService;
import com.example.recordshop.domain.payment.PaymentId;
import com.example.recordshop.domain.payment.PaymentMethod;
import com.example.recordshop.domain.payment.PaymentRepository;
import com.example.recordshop.domain.shared.Address;
import com.example.recordshop.domain.shared.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 起動時にカタログ(Release/Pressing/Listing)・顧客・注文・決済のサンプルデータを一式投入する。
 *
 * <p>{@code schema.sql} が起動のたびにテーブルを作り直す(データを永続化しない)設計のため、
 * {@link com.example.recordshop.infrastructure.security.AdminAccountSeeder} と同様に
 * {@link CommandLineRunner} として毎回冪等に投入する。ADMINアカウントの起票とは完全に独立している
 * (このシーダーはCUSTOMERロールの会員のみ扱う)ため、実行順序の制御は不要。
 *
 * <p>{@link ListingStatus} は6値のうち5値({@code DRAFT}/{@code PUBLISHED}/{@code RESERVED}/
 * {@code SOLD}/{@code OUT_OF_STOCK})を{@link Listing}の正規のビジネスメソッド経由で再現する。
 * {@code REMOVED}へ遷移する公開メソッドは{@link Listing}に存在しないため、そこだけ
 * {@link Listing#reconstitute}を直接使う(意図的な例外。コメント参照)。
 *
 * <p>{@code @Profile("!test")}: 結合テスト({@code @SpringBootTest})は Spring の
 * ApplicationContext キャッシュにより複数のテストクラス間で同じコンテキスト(=同じ H2 インメモリDB)
 * を使い回す。{@link CommandLineRunner} はコンテキスト起動時に一度だけ、各テストメソッドの
 * {@code @Transactional} ロールバック対象外で実行されるため、テスト環境でこのシーダーを動かすと
 * 投入したサンプルデータが後続のテストの前提件数を壊してしまう。そのため test プロファイルでは
 * 無効化する({@code src/test/resources/application.properties} で {@code spring.profiles.active=test}
 * を指定している)。
 */
@Component
@Profile("!test")
public class SampleDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataSeeder.class);

    private final ReleaseRepository releaseRepository;
    private final ListingRepository listingRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderPlacementService orderPlacementService;
    private final PaymentCaptureService paymentCaptureService;
    private final PasswordHasher passwordHasher;

    public SampleDataSeeder(ReleaseRepository releaseRepository,
                             ListingRepository listingRepository,
                             CustomerRepository customerRepository,
                             OrderRepository orderRepository,
                             PaymentRepository paymentRepository,
                             OrderPlacementService orderPlacementService,
                             PaymentCaptureService paymentCaptureService,
                             PasswordHasher passwordHasher) {
        this.releaseRepository = releaseRepository;
        this.listingRepository = listingRepository;
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.orderPlacementService = orderPlacementService;
        this.paymentCaptureService = paymentCaptureService;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public void run(String... args) {
        if (!releaseRepository.findAll().isEmpty()) {
            log.info("サンプルデータは既に投入済みのため起票をスキップしました。");
            return;
        }

        Instant now = Instant.now();
        SeedListings listings = seedReleases(now);
        List<CustomerId> customers = seedCustomers(now);
        seedOrdersAndPayments(listings, customers, now);

        log.info("サンプルデータ(Release 6件、Customer {}名、Order/Payment 4件)を投入しました。", customers.size());
    }

    // ------------------------------------------------------------
    // Release / Pressing / Listing
    // ------------------------------------------------------------

    /** Order作成で使い回す代表Listingの ID をまとめて持ち回すための入れ物。 */
    private record SeedListings(
            ListingId thrillerUsedListingId,
            ListingId selectedAmbientWorksUsedListingId,
            ListingId darkSideOfTheMoonNewListingId,
            ListingId aLongVacationUsedListingId,
            ListingId nevermindNewListingId
    ) {
    }

    private SeedListings seedReleases(Instant now) {
        seedBlueTrain(now);
        ListingId darkSideNewListingId = seedDarkSideOfTheMoon(now);
        ListingId thrillerUsedListingId = seedThriller(now);
        ListingId nevermindNewListingId = seedNevermind(now);
        ListingId aLongVacationUsedListingId = seedALongVacation(now);
        ListingId sawUsedListingId = seedSelectedAmbientWorks(now);

        return new SeedListings(thrillerUsedListingId, sawUsedListingId, darkSideNewListingId,
                aLongVacationUsedListingId, nevermindNewListingId);
    }

    private void seedBlueTrain(Instant now) {
        Release release = Release.register(ReleaseId.generate(), "Blue Train", "John Coltrane",
                Set.of("Jazz", "Hard Bop"), 1957, "/images/artwork/blue-train.svg");

        Pressing lp = release.addPressing("Blue Note", "BLP 1577", "US", 1957, "XSM", false,
                Format.vinyl(MediaType.LP, Speed.RPM_33, 1), "/images/artwork/pressings/blue-train-us-1957.svg");
        Pressing cd = release.addPressing("Blue Note", "CDP 7243 8 32097 2 5", "US", 1997, null, true,
                Format.vinyl(MediaType.CD, Speed.NOT_APPLICABLE, 1), "/images/artwork/pressings/blue-train-us-1997.svg");
        releaseRepository.save(release);

        // 状態の良い方(VG+/VG)は購入可能なまま公開しておく。
        Listing goodCopy = Listing.usedCopy(ListingId.generate(), lp.pressingId(), Money.jpy(58_000),
                GoldmineGrade.VERY_GOOD_PLUS, GoldmineGrade.VERY_GOOD, null);
        goodCopy.publish(now);
        listingRepository.save(goodCopy);

        // 状態がやや落ちる方(GOOD+/GOOD)は既に売り切れたデモとして SOLD まで進めておく。
        Listing wornCopy = Listing.usedCopy(ListingId.generate(), lp.pressingId(), Money.jpy(32_000),
                GoldmineGrade.GOOD_PLUS, GoldmineGrade.GOOD, null);
        wornCopy.publish(now);
        wornCopy.reserve(1);
        wornCopy.confirmSale(1, now);
        listingRepository.save(wornCopy);

        Listing reissueCd = Listing.newCopy(ListingId.generate(), cd.pressingId(), Money.jpy(2_800), 5);
        reissueCd.publish(now);
        listingRepository.save(reissueCd);
    }

    private ListingId seedDarkSideOfTheMoon(Instant now) {
        Release release = Release.register(ReleaseId.generate(), "The Dark Side of the Moon", "Pink Floyd",
                Set.of("Progressive Rock", "Rock"), 1973, "/images/artwork/dark-side-of-the-moon.svg");

        Pressing originalLp = release.addPressing("Harvest", "SHVL 804", "GB", 1973, null, false,
                Format.vinyl(MediaType.LP, Speed.RPM_33, 1),
                "/images/artwork/pressings/dark-side-of-the-moon-gb-1973.svg");
        Pressing reissueLp = release.addPressing("Harvest", "0724347942911", "GB", 2011, null, true,
                Format.vinyl(MediaType.LP, Speed.RPM_33, 1),
                "/images/artwork/pressings/dark-side-of-the-moon-gb-2011.svg");
        releaseRepository.save(release);

        Listing originalCopy = Listing.usedCopy(ListingId.generate(), originalLp.pressingId(), Money.jpy(55_000),
                GoldmineGrade.NEAR_MINT, GoldmineGrade.NEAR_MINT, null);
        originalCopy.publish(now);
        listingRepository.save(originalCopy);

        // 在庫十分な再発盤。この一部を Order C が購入する(placeOrder が自動で在庫を引き当てる)。
        Listing reissueStock = Listing.newCopy(ListingId.generate(), reissueLp.pressingId(), Money.jpy(4_200), 10);
        reissueStock.publish(now);
        listingRepository.save(reissueStock);

        // 在庫1枚だけの限定分。予約済みで在庫切れのデモとして公開する。
        Listing lastCopy = Listing.newCopy(ListingId.generate(), reissueLp.pressingId(), Money.jpy(4_500), 1);
        lastCopy.publish(now);
        lastCopy.reserve(1);
        listingRepository.save(lastCopy);

        return reissueStock.listingId();
    }

    private ListingId seedThriller(Instant now) {
        Release release = Release.register(ReleaseId.generate(), "Thriller", "Michael Jackson",
                Set.of("Pop", "R&B", "Funk"), 1982, "/images/artwork/thriller.svg");

        Pressing originalLp = release.addPressing("Epic", "QE 38112", "US", 1982, null, false,
                Format.vinyl(MediaType.LP, Speed.RPM_33, 1), "/images/artwork/pressings/thriller-us-1982.svg");
        Pressing reissueLp = release.addPressing("Epic", "1907581421", "US", 2018, null, true,
                Format.vinyl(MediaType.LP, Speed.RPM_33, 1), "/images/artwork/pressings/thriller-us-2018.svg");
        releaseRepository.save(release);

        // このListingは公開のみ済ませ、Order A が購入して RESERVED(未決済)のデモになる。
        Listing usedCopy = Listing.usedCopy(ListingId.generate(), originalLp.pressingId(), Money.jpy(12_000),
                GoldmineGrade.VERY_GOOD, GoldmineGrade.VERY_GOOD_MINUS, null);
        usedCopy.publish(now);
        listingRepository.save(usedCopy);

        // 出品準備中(未公開)のデモとして、あえて publish() を呼ばず DRAFT のままにしておく。
        Listing draftReissue = Listing.newCopy(ListingId.generate(), reissueLp.pressingId(), Money.jpy(3_900), 8);
        listingRepository.save(draftReissue);

        return usedCopy.listingId();
    }

    private ListingId seedNevermind(Instant now) {
        Release release = Release.register(ReleaseId.generate(), "Nevermind", "Nirvana",
                Set.of("Grunge", "Alternative Rock"), 1991, "/images/artwork/nevermind.svg");

        Pressing lp = release.addPressing("DGC", "DGC-24425", "US", 1991, null, false,
                Format.vinyl(MediaType.LP, Speed.RPM_33, 1), "/images/artwork/pressings/nevermind-us-1991.svg");
        releaseRepository.save(release);

        Listing usedCopy = Listing.usedCopy(ListingId.generate(), lp.pressingId(), Money.jpy(9_800),
                GoldmineGrade.GOOD, GoldmineGrade.GOOD_PLUS, "盤面にスレ傷あり");
        usedCopy.publish(now);
        listingRepository.save(usedCopy);

        // デッドストックの新品1点。Order D が購入 → 決済 → 返金 → 注文キャンセルされ、最終的に在庫が復帰する。
        Listing newCopy = Listing.newCopy(ListingId.generate(), lp.pressingId(), Money.jpy(15_000), 1);
        newCopy.publish(now);
        listingRepository.save(newCopy);

        return newCopy.listingId();
    }

    private ListingId seedALongVacation(Instant now) {
        Release release = Release.register(ReleaseId.generate(), "A LONG VACATION", "大瀧詠一",
                Set.of("City Pop", "Pop"), 1981, "/images/artwork/a-long-vacation.svg");

        Pressing originalLp = release.addPressing("Sony/CBS", "25AH 1444", "JP", 1981, null, false,
                Format.vinyl(MediaType.LP, Speed.RPM_33, 1),
                "/images/artwork/pressings/a-long-vacation-jp-1981.svg");
        Pressing reissueLp = release.addPressing("Sony Music", "SRJL-1", "JP", 2021, null, true,
                Format.vinyl(MediaType.LP, Speed.RPM_33, 1),
                "/images/artwork/pressings/a-long-vacation-jp-2021.svg");
        releaseRepository.save(release);

        // Order C が購入し、決済確定とともに SOLD まで進める。
        Listing usedCopy = Listing.usedCopy(ListingId.generate(), originalLp.pressingId(), Money.jpy(18_000),
                GoldmineGrade.VERY_GOOD_PLUS, GoldmineGrade.VERY_GOOD_PLUS, null);
        usedCopy.publish(now);
        listingRepository.save(usedCopy);

        Listing reissueCopy = Listing.newCopy(ListingId.generate(), reissueLp.pressingId(), Money.jpy(4_800), 6);
        reissueCopy.publish(now);
        listingRepository.save(reissueCopy);

        return usedCopy.listingId();
    }

    private ListingId seedSelectedAmbientWorks(Instant now) {
        Release release = Release.register(ReleaseId.generate(), "Selected Ambient Works 85-92", "Aphex Twin",
                Set.of("Electronic", "Ambient"), 1992, "/images/artwork/selected-ambient-works-85-92.svg");

        Pressing lp = release.addPressing("Apollo/R&S", "AMB 3922", "GB", 1992, null, false,
                Format.vinyl(MediaType.LP, Speed.RPM_33, 2),
                "/images/artwork/pressings/selected-ambient-works-85-92-gb-1992.svg");
        Pressing cd = release.addPressing("Warp", "WARPCD092", "GB", 2006, null, true,
                Format.vinyl(MediaType.CD, Speed.NOT_APPLICABLE, 1),
                "/images/artwork/pressings/selected-ambient-works-85-92-gb-2006.svg");
        releaseRepository.save(release);

        // Order B が購入し、決済確定とともに SOLD まで進める。
        Listing usedCopy = Listing.usedCopy(ListingId.generate(), lp.pressingId(), Money.jpy(15_000),
                GoldmineGrade.MINT, GoldmineGrade.NEAR_MINT, null);
        usedCopy.publish(now);
        listingRepository.save(usedCopy);

        // Listing には出品取り下げ(REMOVED)へ遷移する公開メソッドが無いため、
        // ここだけ例外的に reconstitute() で REMOVED 状態を直接組み立てる。
        Listing removedCd = Listing.reconstitute(ListingId.generate(), cd.pressingId(), ConditionType.NEW,
                Money.jpy(2_200), ListingStatus.REMOVED, 3, null, null, null);
        listingRepository.save(removedCd);

        return usedCopy.listingId();
    }

    // ------------------------------------------------------------
    // Customer
    // ------------------------------------------------------------

    private record SeedCustomer(String displayName, String email, Address address) {
    }

    private List<CustomerId> seedCustomers(Instant now) {
        List<SeedCustomer> seedCustomers = List.of(
                new SeedCustomer("田中 花子", "tanaka.hanako@example.com",
                        new Address("田中 花子", "150-0001", "東京都", "渋谷区", "1-2-3", "JP")),
                new SeedCustomer("佐藤 次郎", "sato.jiro@example.com",
                        new Address("佐藤 次郎", "530-0001", "大阪府", "大阪市北区", "梅田1-1-1", "JP")),
                new SeedCustomer("鈴木 美咲", "suzuki.misaki@example.com",
                        new Address("鈴木 美咲", "060-0001", "北海道", "札幌市中央区", "北一条西2-3", "JP")),
                new SeedCustomer("高橋 健太", "takahashi.kenta@example.com",
                        new Address("高橋 健太", "810-0001", "福岡県", "福岡市中央区", "天神2-2-2", "JP"))
        );

        List<CustomerId> customerIds = new ArrayList<>();
        int daysAgo = seedCustomers.size();
        for (SeedCustomer seedCustomer : seedCustomers) {
            CustomerId customerId = CustomerId.generate();
            Customer customer = Customer.register(customerId, new Email(seedCustomer.email()),
                    passwordHasher.hash("Passw0rd!2024"), seedCustomer.displayName(),
                    now.minus(daysAgo, ChronoUnit.DAYS));
            customerRepository.save(customer);
            customerIds.add(customerId);
            daysAgo--;
        }
        return customerIds;
    }

    // ------------------------------------------------------------
    // Order / Payment
    // ------------------------------------------------------------

    private void seedOrdersAndPayments(SeedListings listings, List<CustomerId> customerIds, Instant now) {
        CustomerId tanaka = customerIds.get(0);
        CustomerId sato = customerIds.get(1);
        CustomerId suzuki = customerIds.get(2);
        CustomerId takahashi = customerIds.get(3);

        Address tanakaAddress = new Address("田中 花子", "150-0001", "東京都", "渋谷区", "1-2-3", "JP");
        Address satoAddress = new Address("佐藤 次郎", "530-0001", "大阪府", "大阪市北区", "梅田1-1-1", "JP");
        Address suzukiAddress = new Address("鈴木 美咲", "060-0001", "北海道", "札幌市中央区", "北一条西2-3", "JP");
        Address takahashiAddress = new Address("高橋 健太", "810-0001", "福岡県", "福岡市中央区", "天神2-2-2", "JP");

        seedOrderA_pendingPayment(listings, tanaka, tanakaAddress, now);
        seedOrderB_paidAndShippedListingSold(listings, sato, satoAddress, now);
        seedOrderC_shippedMultiLine(listings, suzuki, suzukiAddress, now);
        seedOrderD_cancelledAndRefunded(listings, takahashi, takahashiAddress, now);
    }

    /** Order A: 未決済のまま(PENDING)。銀行振込を選択したが、まだ振り込まれていないデモ。 */
    private void seedOrderA_pendingPayment(SeedListings listings, CustomerId customerId, Address address,
                                            Instant now) {
        Cart cart = Cart.open(CartId.generate(), customerId);
        cart.addLine(listings.thrillerUsedListingId(), ConditionType.USED, 1, now);

        Order order = orderPlacementService.placeOrder(cart, address, address, now);

        Payment payment = Payment.initiate(PaymentId.generate(), order.orderId(), order.totalAmount(),
                PaymentMethod.BANK_TRANSFER);
        paymentRepository.save(payment);
    }

    /** Order B: クレジットカード決済が完了し、在庫(Used)も SOLD まで進んだデモ。 */
    private void seedOrderB_paidAndShippedListingSold(SeedListings listings, CustomerId customerId, Address address,
                                                        Instant now) {
        Cart cart = Cart.open(CartId.generate(), customerId);
        cart.addLine(listings.selectedAmbientWorksUsedListingId(), ConditionType.USED, 1, now);

        Order order = orderPlacementService.placeOrder(cart, address, address, now);
        paymentCaptureService.capturePayment(order.orderId(), PaymentMethod.CREDIT_CARD, now);

        Listing listing = listingRepository.findById(listings.selectedAmbientWorksUsedListingId()).orElseThrow();
        listing.confirmSale(1, now);
        listingRepository.save(listing);
    }

    /** Order C: New×2 + Used×1 の複数明細。決済完了後、発送済み(SHIPPED)まで進めたデモ。 */
    private void seedOrderC_shippedMultiLine(SeedListings listings, CustomerId customerId, Address address,
                                              Instant now) {
        Cart cart = Cart.open(CartId.generate(), customerId);
        cart.addLine(listings.darkSideOfTheMoonNewListingId(), ConditionType.NEW, 2, now);
        cart.addLine(listings.aLongVacationUsedListingId(), ConditionType.USED, 1, now);

        Order order = orderPlacementService.placeOrder(cart, address, address, now);
        paymentCaptureService.capturePayment(order.orderId(), PaymentMethod.CREDIT_CARD, now);

        // Used明細のみ SOLD へ進める(New明細は reserve() 時点で在庫を引き当て済みのため confirmSale 不要)。
        Listing usedListing = listingRepository.findById(listings.aLongVacationUsedListingId()).orElseThrow();
        usedListing.confirmSale(1, now);
        listingRepository.save(usedListing);

        Order paidOrder = orderRepository.findById(order.orderId()).orElseThrow();
        paidOrder.markShipped();
        orderRepository.save(paidOrder);
    }

    /** Order D: 決済まで完了した後、返金・注文キャンセルとなり、在庫も復帰したデモ。 */
    private void seedOrderD_cancelledAndRefunded(SeedListings listings, CustomerId customerId, Address address,
                                                  Instant now) {
        Cart cart = Cart.open(CartId.generate(), customerId);
        cart.addLine(listings.nevermindNewListingId(), ConditionType.NEW, 1, now);

        Order order = orderPlacementService.placeOrder(cart, address, address, now);

        // PaymentRepository.save() はINSERT専用でUPDATE手段が無いため、Capture→Refundまでメモリ上で
        // 進めてから1回だけ保存する(PaymentCaptureServiceを経由すると即CAPTUREDでINSERTされてしまう)。
        Payment payment = Payment.initiate(PaymentId.generate(), order.orderId(), order.totalAmount(),
                PaymentMethod.CREDIT_CARD);
        payment.capture(now);
        payment.refund(now);
        paymentRepository.save(payment);

        order.markPaid();
        orderRepository.save(order);
        order.cancel();
        orderRepository.save(order);

        Listing listing = listingRepository.findById(listings.nevermindNewListingId()).orElseThrow();
        listing.cancelReservation(1);
        listingRepository.save(listing);
    }
}
