# phase-3: Web層(REST API・Thymeleaf画面・Spring Security)(record-shop-ec-mybatis)

> このプロンプトを投げる前に、同じフォルダの `00-common.md` を読んで従うこと。
> 作業ディレクトリ: `<親フォルダ>/record-shop-ec-mybatis`(phase-2 完了状態)

## 1. このフェーズの目的

REST API(`/api/**`)、Thymeleaf 画面(公開・会員・管理者)、Spring Security フォーム認証、
チェックアウトのトランザクション/リトライ、SES メール送信アダプタ、CloudFront 対応フィルタを実装し、
**ブラウザで会員登録 → 購入 → 管理者による注文操作まで一通り動く**状態にする。

## 2. 前提

- phase-2 の 77 テストが green
- **不具合修正済みの姿で作る**(元プロジェクトのシステムテストで見つかった不具合を最初から作り込まない)。特に 3.9 の「Ordering/Payment → Inventory への通知」を必ず実装する

## 3. 仕様

### 3.1 Spring Security(`config/SecurityConfig`)

```java
@Configuration @EnableWebSecurity
public class SecurityConfig {
  @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
  @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) {
    http
      .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
      .authorizeHttpRequests(auth -> auth
          .requestMatchers("/", "/catalog/**", "/register", "/register/confirm", "/login", "/error",
                  "/css/**", "/js/**", "/images/**", "/webjars/**", "/actuator/health", "/api/**",
                  "/robots.txt").permitAll()
          .requestMatchers("/admin/**").hasRole("ADMIN")
          .anyRequest().authenticated())
      .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/catalog", false).permitAll())
      .logout(logout -> logout.logoutSuccessUrl("/catalog").permitAll());
    return http.build();
  }
}
```
- `/api/**` は認証不要 + CSRF 除外(curl・自動テストから直接叩けるようにする意図的判断)
- ロールは `ROLE_CUSTOMER` / `ROLE_ADMIN`。ログイン画面はロール共通、ログイン後は一律 `/catalog`

`infrastructure/security`:
- `BCryptPasswordHasher`(`@Component implements PasswordHasher`、`PasswordEncoder` に委譲)
- `CustomerUserDetails implements UserDetails`(`(Customer)` ctor、`getUsername()`=email、`getPassword()`=passwordHash、authorities = `"ROLE_" + role.name()`、追加で `CustomerId customerId()` / `String displayName()`)
- `CustomerUserDetailsService`(`@Service implements UserDetailsService`、`findByEmail(new Email(username))`、無ければ `UsernameNotFoundException("No customer for email: " + email)`)
- `AdminAccountSeeder`(`@Component implements CommandLineRunner`、`@Value("${app.admin.email:}")` / `@Value("${app.admin.password:}")`):
  - どちらか blank → `log.warn("ADMIN_EMAIL / ADMIN_PASSWORD が未設定のため、管理者アカウントの起票をスキップしました。管理画面(/admin/**)を使うには環境変数を設定して再起動してください。")`
  - 既存 → `log.info("管理者アカウント({})は既に存在するため起票をスキップしました。", email)`(冪等)
  - 新規 → `Customer.reconstitute(CustomerId.generate(), email, hash, "管理者", CustomerRole.ADMIN, Instant.now())` を save、`log.info("管理者アカウント({})を起票しました。", email)`

### 3.2 REST API(`web/**`、`@RestController`)

| メソッド | パス | Controller | リクエスト(JSON) | レスポンス |
|---|---|---|---|---|
| POST | `/api/releases` | `web.catalog.ReleaseController` | `RegisterReleaseRequest{title, artistName, genres:Set<String>, originalReleaseYear:int, artworkUrl}` | 201 `ReleaseResponse` |
| GET | `/api/releases/{releaseId}` | 同上 | - | 200 `ReleaseResponse` / 404 |
| POST | `/api/releases/{releaseId}/pressings` | 同上 | `AddPressingRequest{labelName, catalogNumber, country, pressYear:int, matrixRunout, reissue:boolean, mediaType:MediaType, speed:Speed, discCount:int, artworkUrl}` | 201 `PressingResponse` / 404 |
| POST | `/api/listings` | `web.inventory.ListingController` | `CreateListingRequest{pressingId, conditionType, priceAmount:BigDecimal, priceCurrency(null→JPY), initialStock:Integer(null→1), vinylGrade, sleeveGrade, sellerNote}` | 201 `ListingResponse` / 404(pressing未存在) |
| POST | `/api/listings/{listingId}/publish` | 同上 | - | 200 `ListingResponse` |
| GET | `/api/listings/{listingId}` | 同上 | - | 200 / 404 |
| POST | `/api/checkout` | `web.ordering.OrderController`(`@Transactional`) | `CheckoutRequest{customerId(省略可→generate), lines:List<CheckoutLineRequest{listingId, conditionType, quantity}>, shippingAddress:AddressRequest, billingAddress:AddressRequest}` → `Cart.open(CartId.generate(), customerId)` を都度組み立てて `OrderPlacementService` | 201 `OrderResponse` |
| GET | `/api/orders/{orderId}` | 同上 | - | 200 / 404 |
| POST | `/api/orders/{orderId}/payments` | `web.payment.PaymentController`(`@Transactional`) | `CapturePaymentRequest{method:PaymentMethod}` → `PaymentCaptureService` + **各Listingへ `confirmSale`**(3.9) | 201 `PaymentResponse` |
| GET | `/api/payments/{paymentId}` | 同上 | - | 200 / 404 |

レスポンス DTO(record + `static from(domain)`):
- `ReleaseResponse(releaseId, title, artistName, genres, originalReleaseYear, artworkUrl, List<PressingResponse> pressings)`
- `PressingResponse(pressingId, labelName, catalogNumber, country, pressYear, reissue, artworkUrl)`
- `ListingResponse(listingId, pressingId, conditionType, priceAmount, priceCurrency, status, stockQuantity, vinylGrade, sleeveGrade, sellerNote)`
- `OrderResponse(orderId, customerId, List<OrderLineResponse> lines, totalAmount, totalCurrency, status)`、`OrderLineResponse(listingId, releaseTitle, artistName, catalogNumber, unitPriceAmount, unitPriceCurrency, quantity)`
- `PaymentResponse(paymentId, orderId, amount, currency, method, status, capturedAt)`
- `AddressRequest(recipientName, postalCode, prefecture, city, addressLine, country)` + `Address toDomain()`
- `ErrorResponse(String message)`

`web/ApiExceptionHandler`(`@RestControllerAdvice`):

| 例外 | ステータス | メッセージ |
|---|---|---|
| `MalformedIdentifierException` | 404 | `e.getMessage()` |
| `InvariantViolationException`, `IllegalArgumentException` | 400 | `e.getMessage()` |
| `IllegalStateTransitionException` | 409 | `e.getMessage()` |
| `OptimisticLockingFailureException`(Spring基底) | 409 | `他の注文と同時に処理されたため確定できませんでした。もう一度お試しください。` |

`web/PathIds`: `static <T> T parse(String raw, Function<String,T> factory, String resourceName)` — `MalformedIdentifierException` を `ResponseStatusException(NOT_FOUND, resourceName + " not found: " + raw)` に包み直す。**画面系コントローラのパス変数はすべてこれを通す**(UUID形式でないIDに `@RestControllerAdvice` が JSON を返してしまうのを防ぎ、404 の HTML にする)。

### 3.3 画面ルーティング(`@Controller`)

| メソッド | パス | Controller | ビュー / 遷移 | モデル属性・備考 |
|---|---|---|---|---|
| GET | `/` | `web.customer.HomeController` | `redirect:/catalog` | |
| GET | `/catalog` | `web.catalog.CatalogPageController` | `catalog/list` | `releases`: **PUBLISHED な Listing を1つ以上持つ Release のみ** |
| GET | `/catalog/{releaseId}` | 同上 | `catalog/detail` / 404 | `release`, `pressingListings: Map<Pressing, List<Listing>>`(PUBLISHED のみ)。購入可能な出品が無い Pressing は「現在購入可能な出品はありません。」 |
| GET | `/login` | `web.customer.LoginController` | `login` | `?registered` / `?error` / `?logout` でメッセージ切替。入力欄は `autocomplete="off"` |
| GET/POST | `/register` | `web.customer.RegistrationController` | `register` / 成功: `redirect:/register/confirm?email={URLエンコード}` | `registerForm`。検証: 表示名 blank → `表示名を入力してください`、パスワード8文字未満 → `パスワードは8文字以上で入力してください`、Email形式不正 → `メールアドレスの形式が正しくありません`、`InvariantViolationException`(登録済み等)→ そのメッセージ、`EmailDeliveryException` → `確認コードのメールを送信できませんでした。メールアドレスをご確認のうえ、もう一度お試しください`。失敗時は `register` を再表示(500にしない) |
| GET | `/register/confirm?email=` | `web.customer.EmailVerificationController` | `register-confirm` | `confirmForm`(email をセット) |
| POST | `/register/confirm` | 同上 | 成功: `redirect:/login?registered` / 失敗: `register-confirm` + `errorMessage` | **`@ModelAttribute("confirmForm") EmailVerificationForm form` と名前を明示**(既定名だと `th:object="${confirmForm}"` が解決できず500) |
| GET | `/cart` | `web.ordering.CartController` | `cart/view` | `lines: List<CartLineView>`, `total: Money` |
| POST | `/cart/add`(`listingId`, `quantity`=1) | 同上 | `redirect:/cart` / Listing 未存在・ID不正: `redirect:/catalog` + flash `errorMessage="指定された商品が見つかりませんでした"` | `catalog/list.html` に flash 表示要素を置く |
| POST | `/cart/remove`(`listingId`) | 同上 | `redirect:/cart` | |
| GET | `/checkout` | `web.ordering.CheckoutController` | `checkout/form` / カート空: `redirect:/cart` + flash `errorMessage="カートが空です"` | `lines`, `total`, `checkoutForm` |
| POST | `/checkout` | 同上 | 成功: `redirect:/orders/{orderId}` + セッションの cart 削除 / 失敗: `redirect:/cart` + flash `errorMessage` | 3.6 参照。**`@Transactional` を付けない** |
| GET | `/orders` | `web.ordering.OrderHistoryController` | `orders/list` | ログイン会員自身の注文のみ、新しい順 |
| GET | `/orders/{orderId}` | 同上 | `orders/detail` / 他人の注文は 404 | `order`, `payments`(「お支払い状況」として `PaymentStatus` を表示) |
| GET | `/admin/releases` | `web.admin.AdminReleaseController` | `admin/releases/list` | 全 Release と Pressing 数 |
| GET | `/admin/releases/new` | 同上 | `admin/releases/new` | `releaseForm` |
| POST | `/admin/releases` | 同上 | 成功: `redirect:/admin/releases/{id}` / 失敗: `admin/releases/new` + `errorMessage` | |
| GET | `/admin/releases/{releaseId}` | 同上 | `admin/releases/detail` | `release`, `pressingListings`(**全ステータスの Listing**), `pressingForm`, `listingForm`, `artworkForm` |
| POST | `/admin/releases/{releaseId}/artwork` | 同上 | `redirect:/admin/releases/{id}` + flash `notice="アートワークを更新しました"` | |
| POST | `/admin/releases/{releaseId}/pressings/{pressingId}/artwork` | 同上 | 同上 / 失敗 flash `errorMessage` | |
| POST | `/admin/releases/{releaseId}/pressings` | 同上 | `redirect:/admin/releases/{id}` / 失敗 flash `errorMessage` | 型変換エラー(pressYear に文字列等)は 400 のままでよい |
| POST | `/admin/listings` | `web.admin.AdminListingController` | `redirect:/admin/releases/{releaseId}` / 失敗 flash `errorMessage="出品の登録に失敗しました: " + msg` | |
| POST | `/admin/listings/{listingId}/publish` | 同上 | `redirect:/admin/releases/{releaseId}` / 失敗 flash `errorMessage` | |
| GET | `/admin/orders` | `web.admin.AdminOrderController` | `admin/orders/list` | 全顧客の注文、新しい順。列: 注文日時・注文番号(先頭8桁)・出荷先宛名・ステータス・合計金額 |
| GET | `/admin/orders/{orderId}` | 同上 | `admin/orders/detail` | 明細(スナップショット)・出荷先・請求先。現在ステータスから遷移可能な操作ボタンのみ表示 |
| POST | `/admin/orders/{orderId}/mark-paid` | 同上 | `redirect:/admin/orders/{id}` + flash `notice="入金を確認しました"` | PENDING→PAID。**PAID になったら各Listingへ `confirmSale`**(3.9) |
| POST | `/admin/orders/{orderId}/mark-shipped` | 同上 | 同上 `notice="発送済みにしました"` | PAID→SHIPPED |
| POST | `/admin/orders/{orderId}/mark-delivered` | 同上 | 同上 `notice="配達完了にしました"` | SHIPPED→DELIVERED |
| POST | `/admin/orders/{orderId}/cancel` | 同上 | 同上 `notice="注文をキャンセルしました"` | PENDING/PAID→CANCELLED。**各Listingへ `cancelReservation`**(3.9) |

- `AdminOrderController` は `transitionAndRedirect(orderId, Consumer<Order>, successMessage, RedirectAttributes)` で4操作を共通化。`IllegalStateTransitionException` は flash `errorMessage` にして詳細画面に留まる
- 権限マトリクス(phase-6/7 の根拠):

| 画面区分 | 匿名 | CUSTOMER | ADMIN |
|---|---|---|---|
| 公開(`/`, `/catalog/**`, `/login`, `/register`, `/register/confirm`) | ○ | ○ | ○ |
| 会員(`/cart`, `/checkout`, `/orders/**`) | 302 `/login` | ○ | ○ |
| 管理(`/admin/**`) | 302 `/login` | 403 | ○ |

### 3.4 フォームオブジェクト(可変クラス、`th:field` 用、`web/**`)

- `RegisterForm{email="", password="", displayName=""}`
- `EmailVerificationForm{email="", code=""}`
- `CheckoutForm{recipientName="", postalCode="", prefecture="", city="", addressLine="", country="JP"}`(配送先・請求先兼用)
- `ReleaseForm{title="", artistName="", genres=""(カンマ区切り), originalReleaseYear:int, artworkUrl=""}`
- `PressingForm{labelName="", catalogNumber="", country="", pressYear:int, matrixRunout="", reissue:boolean, mediaType=LP, speed=RPM_33, discCount=1, artworkUrl=""}`
- `ListingForm{pressingId="", conditionType=NEW, priceAmount:BigDecimal, priceCurrency="JPY", initialStock=1, vinylGrade, sleeveGrade, sellerNote=""}`
- `ArtworkForm{artworkUrl=""}`

### 3.5 テンプレート(`src/main/resources/templates`)と CSS

15ファイル: `login.html`, `register.html`, `register-confirm.html`, `fragments/nav.html`, `catalog/list.html`, `catalog/detail.html`, `cart/view.html`, `checkout/form.html`, `orders/list.html`, `orders/detail.html`, `admin/releases/list.html`, `admin/releases/new.html`, `admin/releases/detail.html`, `admin/orders/list.html`, `admin/orders/detail.html`

- 全ページ `<div th:replace="~{fragments/nav :: nav}"></div>` と `<link rel="stylesheet" th:href="@{/css/main.css}"/>`
- `fragments/nav.html`(`th:fragment="nav"`、`xmlns:sec` 使用): ブランド `💿 Record Shop`(→`/catalog`)、リンク「商品一覧」「カート」、認証済みなら「注文履歴」+ ADMIN のみ「管理画面」(`/admin/releases`)「注文管理」(`/admin/orders`)+ `<span sec:authentication="principal.displayName">さん` + `POST /logout` フォームの「ログアウト」ボタン、未認証なら「ログイン」「会員登録」
- `static/css/main.css`: シンプルなダーク寄りデザイン。クラス: `site-header` `brand` `card` `form-card` `field` `btn` `btn-secondary` `error` `notice` `muted` `simple`(table) `status-badge` `release-list` `release-card` `artwork-thumb` `artwork-preview` `artwork-hero`
- `catalog/list.html`: Release をカード一覧(artworkUrl があればサムネイル)、flash `errorMessage` の表示要素あり
- `catalog/detail.html`: 作品全体のジャケット(大)、Pressing ごとに公開済み Listing(コンディション・グレード・価格・在庫)+「カートに追加」フォーム(`POST /cart/add`)、Pressing 個別画像
- `cart/view.html`: 明細・合計・「レジに進む」(`/checkout`)、`errorMessage` 表示
- `checkout/form.html`: 明細再掲 + 住所6項目 + 送信
- `orders/detail.html`: 注文番号・注文日時・ステータス・明細(スナップショット)・**お支払い状況(Payment ステータス)**
- `admin/releases/detail.html`: 1画面に5機能 — ①Pressing ごとの Listing 一覧 + 公開ボタン、②Listing 作成フォーム(pressingId 隠し、conditionType、priceAmount、initialStock(NEW)、vinylGrade/sleeveGrade/sellerNote(USED))、③Pressing 追加フォーム、④作品全体のアートワークURL フォーム(現在画像プレビュー付き)、⑤Pressing ごとのアートワークURL フォーム(折りたたみ)。Enum セレクトは `${T(com.example.recordshop.domain.inventory.GoldmineGrade).values()}` 等で展開
- `admin/orders/detail.html`: 操作ボタンの表示条件 — 「入金を確認する」(PENDING)、「発送済みにする」(PAID)、「配達完了にする」(SHIPPED)、「注文をキャンセル」(PENDING または PAID)。成功は緑の `notice`、失敗は `error`

### 3.6 カート・チェックアウト(`web/ordering`)

- `CartController`: `HttpSession` にキー `"cart"` で `Cart` を保持(DB永続化しない。サーバー再起動・ECSタスク入れ替えで消える)。`currentCart(principal, session)` で無ければ `Cart.open(CartId.generate(), principal.customerId())`。ID解析失敗は `Optional.empty()` 扱い
- `CartLineView`(record: listingId, releaseTitle, artistName, catalogNumber, conditionType, gradeText, unitPrice, quantity, lineTotal 等)
- `CartViewAssembler`(package-private final): `assemble(Cart, ListingRepository, ReleaseRepository)` / `total(List<CartLineView>)`。Listing/Release/Pressing が引けない行はスキップ。**PUBLISHED でない Listing(売り切れ・非公開)は行に含めず、`Cart` からも `removeLine` して flash で「『{作品名}』は売り切れのためカートから削除しました」と知らせる**(カートが行き止まりにならないようにする)
- `CheckoutService`(`@Service`、`(OrderPlacementService, PaymentCaptureService, ListingRepository, PlatformTransactionManager)`、`TransactionTemplate` を内部生成、`MAX_ATTEMPTS = 3`):
  - `Order checkout(Cart cart, Address shipping, Address billing)`: 最大3回、`placeOrderAndCapture` を `TransactionTemplate.execute` 内で実行。`OptimisticLockingFailureException` を捕まえたら `LOGGER.info("在庫の楽観ロック競合を検知したため注文処理をリトライします(試行 {}/{})", attempt, MAX_ATTEMPTS)` してリトライ、全滅で最後の例外を throw
  - `placeOrderAndCapture`: `orderPlacementService.placeOrder(...)` → `paymentCaptureService.capturePayment(orderId, CREDIT_CARD, now)` → **各 OrderLine の Listing に `confirmSale(quantity, now)` して save**(USED は SOLD、NEW は状態そのまま)
  - 設計理由: `@Transactional` をコントローラに付けて内側で例外捕捉すると rollback-only 状態でコミットして `UnexpectedRollbackException` → 500 になる。例外捕捉はトランザクション境界の外側で行う。自クラス呼び出しではプロキシが効かないため `TransactionTemplate`
- `CheckoutController#submit`: `CheckoutForm` → `Address`(配送先=請求先)。例外 → flash `errorMessage` で `redirect:/cart`:
  - `OptimisticLockingFailureException`(リトライ全滅)→ `他の注文と同時に処理されたため確定できませんでした。もう一度お試しください。`
  - `InvariantViolationException` / `IllegalStateTransitionException`(売り切れ・在庫不足等)→ **利用者向け文言に変換**: `売り切れのため確定できませんでした: {作品名}`(内部例外メッセージ `Listing のステータスを RESERVED から RESERVED へ変更することはできません` をそのまま出さない)。作品名は Cart の各行から特定する
  - `IllegalArgumentException`(住所検証)→ そのメッセージ(例: 国コードの案内文言)で `redirect:/checkout`

### 3.7 メール送信(`config/MailConfig`, `infrastructure/mail/SesEmailSender`)

- `MailConfig`: `@Bean SesClient sesClient(@Value("${app.mail.aws.region}") String region)` = `SesClient.builder().region(Region.of(region)).build()`
- `SesEmailSender`(`@Component implements EmailSender`、`(SesClient, @Value("${app.mail.from-address}") String fromAddress)`):
  - `sendVerificationCode`: 件名 `【Record Shop】会員登録の確認コード`、本文 `{displayName} 様\n\n以下の確認コードを会員登録画面に入力してください。\n\n確認コード: {code}\n\n有効期限は10分間です。`
  - `sendRegistrationCompleted`: 件名 `【Record Shop】会員登録が完了しました`、本文 `{displayName} 様\n\n会員登録が完了しました。ログインしてご利用いただけます。`
  - `SdkException` を `EmailDeliveryException("メールの送信に失敗しました: " + to.value(), e)` に包み直す。UTF-8 指定
- ローカル開発で SES を使いたくない場合に備え、`@Profile("local-mail-log")` 等でログ出力するスタブを置いてもよい(任意)

### 3.8 フィルタ・その他設定

- `config/WebConfig`: `FilterRegistrationBean<CloudFrontProtoFilter>`(`Ordered.HIGHEST_PRECEDENCE`)、`FilterRegistrationBean<NoIndexHeaderFilter>`(`HIGHEST_PRECEDENCE + 1`)
- `infrastructure/web/CloudFrontProtoFilter`: リクエストヘッダー `X-Forwarded-Proto-Cf` が `https` のとき、`HttpServletRequestWrapper` で `getScheme()="https"` / `isSecure()=true` / `getServerPort()=443` を上書きし、`HttpServletResponseWrapper` で `sendRedirect` の Location を `https://` 絶対URLに組み立て直す。理由: CloudFront(HTTPS)→ALB(HTTP)→Fargate 構成で ALB が `X-Forwarded-Proto` を毎回 `http` に上書きするため、標準の `server.forward-headers-strategy` では HTTPS を認識できず、ログイン後リダイレクトの Location が `http://` になる(元プロジェクトで実際に発生)
- `infrastructure/web/NoIndexHeaderFilter`: 全レスポンスに `X-Robots-Tag: noindex, nofollow`
- `config/DomainServiceConfig`: `@Bean` で `OrderPlacementService`, `PaymentCaptureService`, `CustomerRegistrationService`, `EmailVerificationService` を組み立てる(ドメイン層に `@Service` を付けない)

### 3.9 Ordering / Payment → Inventory への通知(必須。元プロジェクトの未修正不具合#9を最初から潰す)

| タイミング | 呼ぶもの | 効果 |
|---|---|---|
| チェックアウトの決済確定後(`CheckoutService`)/ REST `POST /api/orders/{id}/payments` / 管理画面「入金を確認する」で PAID になった時 | 各 OrderLine の Listing に `confirmSale(quantity, now)` + save | USED は RESERVED→SOLD(`ListingSold` 発行)。NEW は変化なし |
| 管理画面「注文をキャンセル」(PENDING/PAID→CANCELLED) | 各 OrderLine の Listing に `cancelReservation(quantity)` + save | USED は RESERVED→PUBLISHED、NEW は在庫が戻る(OUT_OF_STOCK なら PUBLISHED) |

- `confirmSale` は冪等でないため、既に SOLD の USED に対して再度呼ばれるケース(REST とチェックアウトの二重)は `IllegalStateTransitionException` になる。PAID 遷移が成功したときだけ呼ぶこと
- 売り切れ(他人が先に買った USED)を持つカートでチェックアウトすると、`reserve` が `IllegalStateTransitionException` を投げる。3.6 の文言変換で「売り切れのため確定できませんでした: {作品名}」を表示する

### 3.10 テスト(`src/test/java/com/example/recordshop/web/**`, `infrastructure/mail`)

| クラス | 種別 | メソッド |
|---|---|---|
| `web/PageRenderingTest` | `@SpringBootTest @AutoConfigureMockMvc`、`@MockitoBean EmailSender emailSender`、`spring-security-test` の `.with(csrf())` / `.with(user(principal))` | `商品一覧にはPUBLISHEDな出品を持つ作品だけが並ぶ` / `商品詳細にUUIDでないIDを渡すと404になる` / `形式が正しい未存在UUIDも404になる` / `確認コードを間違えても画面が描画されエラーメッセージが表示される` / `確認コードメールの送信に失敗したら登録画面にエラーメッセージを出す` / `存在しない商品をカートに入れようとすると商品一覧にメッセージが表示される` / `注文詳細に決済状態が表示される` / **`チェックアウトで決済が確定するとUSED出品はSOLDになる`** / **`管理画面で注文をキャンセルすると在庫が戻る`** / **`売り切れた出品を含むカートでチェックアウトすると利用者向けの文言でカートに戻される`** |
| `web/ordering/CheckoutServiceTest` | 純ユニット(InMemory + NOOP `PlatformTransactionManager` + `FlakyListingRepository`(最初のN回だけ save で `OptimisticLockingFailureException`)) | `checkout_競合しなければ注文確定と決済Captureが行われる` / `checkout_楽観ロック競合が起きても引き直して成功する`(saveAttempts==2) / `checkout_リトライ上限まで競合し続けたら例外を伝播する`(saveAttempts==3) |
| `infrastructure/mail/SesEmailSenderTest` | Mockito | `sendVerificationCode_正しい宛先_送信元_確認コードを含む本文で送信される` / `sendRegistrationCompleted_正しい宛先_送信元で送信される` / `送信に失敗したらSDK例外ではなくEmailDeliveryExceptionを投げる` |

`PageRenderingTest` のヘルパー: `seedRelease(title)`(品番に `System.nanoTime()` を混ぜて一意制約回避)、`seedListing(pressing, publish, stock)`、`seedCustomer()`(`CustomerUserDetails` を返す)。合計 **16件**(累計 93件)。

## 4. 設計上の注意・落とし穴

- `CheckoutController#submit` に `@Transactional` を付けない(境界は `CheckoutService`)
- `EmailVerificationController#confirm` の `@ModelAttribute("confirmForm")` 名の明示を忘れない
- 画面系のパス変数は `PathIds.parse` を通し、UUID 形式でない値は 404(JSON ではなく HTML)
- `CatalogPageController#list` は `findAll()` を**そのまま渡さない**。PUBLISHED Listing を持つ Release だけに絞る(詳細側だけ絞って一覧側を忘れるのが元プロジェクトの不具合#1)
- 国コードは `Address`/`Pressing` のドメイン検証で弾く(DB の `VARCHAR(2)` で500になる前に、`国コードはISO 3166-1 alpha-2形式の大文字2文字で入力してください(例: JP)` を画面に出す)
- `PageRenderingTest` では `EmailSender` を必ずモックする(実 SES を叩かない)。SES 認証情報が無い環境でも `SesClient` Bean 生成自体は失敗しない(送信時にのみ失敗する)
- `SecurityConfig` の permitAll に `/images/**` と `/robots.txt` を含める(phase-4 のアートワーク・robots 用)

## 5. 完了条件

- `./mvnw test` が green(累計 93件)
- PostgreSQL を起動し、`ADMIN_EMAIL=admin@example.com ADMIN_PASSWORD=Passw0rd!2024 ./mvnw spring-boot:run` で起動
- 手動確認(ブラウザ or curl):
  1. `http://localhost:8080/login` で管理者ログイン → `/admin/releases/new` で作品登録 → 詳細でPressing追加 → Listing(NEW 在庫2 と USED)作成 → 公開
  2. `/catalog` に作品が出る。未公開の作品は出ない
  3. `curl -X POST /api/releases` で JSON 登録が 201
  4. 会員登録(SES 未設定なら `EmailDeliveryException` のエラー文言が画面に出ることを確認。SES 設定済みなら 6 桁コードが届き `/register/confirm` で完了)
  5. サンプル会員(phase-4 で投入。ここでは新規会員か管理者)でカート → チェックアウト → `/orders/{id}` に「お支払い状況: CAPTURED」、USED 出品が管理画面で SOLD になっている
  6. `/admin/orders` で注文が見え、キャンセルすると NEW 在庫が戻る
  7. 匿名で `/cart` → `/login` へ 302、CUSTOMER で `/admin/releases` → 403

## 6. コミット

- `フェーズ3: Spring Security・REST API・Thymeleaf画面を追加`
- `フェーズ3: 会員登録のメール確認コード送信・照合機能を追加`
- `フェーズ3: 管理画面に注文一覧・注文詳細と在庫連動(confirmSale/cancelReservation)を追加`
- `フェーズ3: 画面をレンダリングして検証する回帰テストを追加`
