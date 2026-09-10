# phase-1: プロジェクト骨格 + ドメイン層(record-shop-ec-mybatis)

> このプロンプトを投げる前に、同じフォルダの `00-common.md` を読んで従うこと。
> 作業ディレクトリ: `<親フォルダ>/record-shop-ec-mybatis`(空フォルダを新規作成して開く)

## 1. このフェーズの目的

Spring Boot プロジェクトの骨格を作り、**ドメイン層(5つのBounded Context + shared)を Spring 非依存の素の Java で実装**する。
永続化は `infrastructure/memory` のインメモリ実装のみ。ドメイン単体テストがすべて green になった状態で終える。

## 2. 前提

- `00-common.md` の 0.5(技術スタック)・0.6(コード規約)・0.7(用語)に従う
- このフェーズでは Web層・MyBatis・DB は作らない(phase-2/3 で追加)。ただし `pom.xml` の依存は最終形で入れてよい

## 3. 仕様

### 3.1 プロジェクト骨格

- Maven Wrapper(`mvnw`, `mvnw.cmd`, `.mvn/wrapper/`)を同梱。Maven 3.9.x
- `pom.xml`:
  - parent: `org.springframework.boot:spring-boot-starter-parent:3.5.x`(`<relativePath/>`)
  - groupId `com.example.recordshop` / artifactId `record-shop-ec-mybatis` / version `0.1.0` / name `Record Shop EC MyBatis`
  - properties: `java.version=21`、`mybatis-spring-boot.version=3.0.x`、`aws-sdk.version=2.29.x`
  - dependencyManagement: `software.amazon.awssdk:bom:${aws-sdk.version}`(pom/import)
  - dependencies:

    | artifact | scope | 用途 |
    |---|---|---|
    | `org.mybatis.spring.boot:mybatis-spring-boot-starter` | - | MyBatis |
    | `spring-boot-starter-jdbc` / `-web` / `-security` / `-thymeleaf` / `-actuator` | - | |
    | `org.thymeleaf.extras:thymeleaf-extras-springsecurity6` | - | `sec:authorize` |
    | `org.postgresql:postgresql` | runtime | |
    | `com.h2database:h2` | test | PostgreSQL互換モードでテスト |
    | `spring-boot-starter-test`、`org.springframework.security:spring-security-test` | test | |
    | `software.amazon.awssdk:ses` | - | 会員登録メール |
  - plugin: `spring-boot-maven-plugin`
- 起動クラス `com.example.recordshop.RecordShopMybatisApplication`:`@SpringBootApplication` + `@MapperScan("com.example.recordshop.infrastructure.mybatis")`
- `.gitignore`: `target/`, `*.class`, `.idea/`, `*.iml`
- `src/main/resources/application.yml` はこのフェーズでは最小(`spring.application.name: record-shop-ec-mybatis`)。phase-2 で全文を確定する

### 3.2 domain/shared

| クラス | 種別 | 仕様 |
|---|---|---|
| `Money` | record `(BigDecimal amount, Currency currency)` | `public static final Currency JPY`。amount/currency null禁止、`amount.signum() < 0` は `IllegalArgumentException`(0は許容)。`static jpy(long)`、`static zero(Currency)`、`add(Money)`(通貨不一致は `IllegalArgumentException("currency mismatch: %s vs %s")`)、`multiply(int)`(負数禁止)、`isGreaterThan(Money)`。`toString()` = `currency.getCurrencyCode() + " " + amount.toPlainString()`(画面に `JPY 4200` と出る) |
| `Address` | record `(recipientName, postalCode, prefecture, city, addressLine, country)` | 全項目 non-blank。`country` は `CountryCodes.requireValid(country, "country")`。フィールド単位の変更は無く差し替えのみ |
| `CountryCodes` | final utility | `Pattern ALPHA2 = "[A-Z]{2}"`。`static String requireValid(String country, String field)`: blank → `IllegalArgumentException(field + " must not be blank")`、不一致 → `IllegalArgumentException("国コードはISO 3166-1 alpha-2形式の大文字2文字で入力してください(例: JP): " + country)` |
| `Identifiers` | final utility | `static UUID parse(String uuid, String idTypeName)`: null → `MalformedIdentifierException(idTypeName + " が指定されていません")`、`UUID.fromString` 失敗 → `MalformedIdentifierException(idTypeName + " の形式が不正です: " + uuid)` |
| `InvariantViolationException` / `IllegalStateTransitionException` / `MalformedIdentifierException` | 例外 | 00-common 0.6 参照 |
| `event/DomainEvent` | interface | `Instant occurredAt();` |

### 3.3 Catalog コンテキスト(`domain/catalog`)

**Enum**
- `MediaType`: `LP, EP, SINGLE_7INCH, SINGLE_10INCH, SINGLE_12INCH, CASSETTE, CD`
- `Speed`: `RPM_33, RPM_45, RPM_78, NOT_APPLICABLE`

**`Format`** record `(MediaType mediaType, Speed speed, int discCount)`: `discCount < 1` → `IllegalArgumentException("discCount must be >= 1: " + discCount)`。`static Format vinyl(MediaType, Speed, int)`

**ID**: `ReleaseId(UUID)`, `PressingId(UUID)`(規約どおり generate/of)

**`Pressing`**(子エンティティ、final class)
- フィールド: `PressingId pressingId`, `String labelName`, `String catalogNumber`, `String country`, `int pressYear`, `String matrixRunout`(nullable), `boolean reissue`, `Format format`(以上final)、`String artworkUrl`(可変、nullable)
- コンストラクタは**パッケージプライベート**(Release経由でのみ生成)。`public static Pressing reconstitute(...)` は永続化層用
- 検証: labelName/catalogNumber non-blank、country は `CountryCodes.requireValid`、`pressYear < 1877 || pressYear > 2100` → `IllegalArgumentException("pressYear is not plausible: " + year)`(1877=蓄音機発明年)、format non-null
- `String identityKey()`(パッケージプライベート)= `catalogNumber + "|" + country + "|" + pressYear`
- `void changeArtworkUrl(String)`(パッケージプライベート)
- getter: `pressingId() labelName() catalogNumber() country() pressYear() matrixRunout() isReissue() format() artworkUrl()`

**`Release`**(集約ルート、final class)
- フィールド: `ReleaseId releaseId`, `String title`, `String artistName`, `Set<String> genres`(unmodifiable `LinkedHashSet`、順序保持), `int originalReleaseYear`(以上final)、`String artworkUrl`(可変)、`List<Pressing> pressings = new ArrayList<>()`
- `static Release register(ReleaseId, String title, String artistName, Set<String> genres, int originalReleaseYear, String artworkUrl)` — title/artistName non-blank、artworkUrl は blank → null に正規化
- `static Release reconstitute(..., List<Pressing> existingPressings)`
- `Pressing addPressing(String labelName, String catalogNumber, String country, int pressYear, String matrixRunout, boolean reissue, Format format, String artworkUrl)` — **不変条件**: 同一Release内で `identityKey()` が重複したら `InvariantViolationException("同一 Release 内に品番・製造国・製造年が重複する Pressing は登録できません: " + catalogNumber + " / " + country + " / " + pressYear)`。生成した Pressing を返す
- `void changeArtworkUrl(String)`(blank→null)
- `Optional<Pressing> findPressing(PressingId)`
- `void changePressingArtworkUrl(PressingId, String)` — 未存在は `IllegalArgumentException("Pressing not found: " + pressingId)`
- `List<Pressing> pressings()`(unmodifiable)、各getter、`equals/hashCode` は releaseId、`toString()` = `"Release{%s - %s, pressings=%d}"`

**`ReleaseRepository`**
```java
void save(Release release);
Optional<Release> findById(ReleaseId id);
List<Release> findAll();
Optional<Release> findByPressingId(PressingId pressingId);
```

### 3.4 Inventory コンテキスト(`domain/inventory`)

**Enum**
- `ConditionType`: `NEW, USED`
- `GoldmineGrade`: `MINT, NEAR_MINT, VERY_GOOD_PLUS, VERY_GOOD, VERY_GOOD_MINUS, GOOD_PLUS, GOOD, FAIR, POOR`
- `ListingStatus`: `DRAFT, PUBLISHED, RESERVED, SOLD, OUT_OF_STOCK, REMOVED`。許可遷移テーブル:

  | from | to |
  |---|---|
  | DRAFT | PUBLISHED, REMOVED |
  | PUBLISHED | RESERVED, OUT_OF_STOCK, REMOVED |
  | RESERVED | PUBLISHED, SOLD |
  | OUT_OF_STOCK | PUBLISHED, REMOVED |
  | SOLD | (終端) |
  | REMOVED | (終端) |

  `canTransitionTo(next)`、`isTerminal()`(SOLD/REMOVED)

**ID**: `ListingId(UUID)`

**イベント**(`event/`): `ListingPublished(ListingId listingId, PressingId pressingId, Instant occurredAt)`、`ListingSold(ListingId listingId, Instant occurredAt)`

**`Listing`**(集約ルート、final class)
- フィールド: `ListingId listingId`, `PressingId pressingId`, `ConditionType conditionType`(final)、`Money price`, `ListingStatus status`, `Integer stockQuantity`(NEW専用)、`GoldmineGrade vinylGrade`, `GoldmineGrade sleeveGrade`, `String sellerNote`(USED専用)、`List<DomainEvent> pendingEvents`
- 生成時 status は `DRAFT`
- `static Listing newCopy(ListingId, PressingId, Money price, int initialStock)` — `initialStock < 1` → `InvariantViolationException("initialStock must be >= 1: " + initialStock)`
- `static Listing usedCopy(ListingId, PressingId, Money price, GoldmineGrade vinylGrade, GoldmineGrade sleeveGrade, String sellerNote)` — 両グレード non-null
- `static Listing reconstitute(ListingId, PressingId, ConditionType, Money, ListingStatus, Integer stockQuantity, GoldmineGrade, GoldmineGrade, String sellerNote)`
- `void publish(Instant now)` — DRAFT→PUBLISHED、`ListingPublished` を積む
- `void reserve(int quantity)`:
  - `quantity < 1` → `InvariantViolationException("quantity must be >= 1: " + quantity)`
  - USED: `quantity != 1` → `InvariantViolationException("Used Listing の数量は常に 1 です: " + quantity)`。PUBLISHED→RESERVED
  - NEW: `requireStatus(PUBLISHED, "在庫を予約")`。在庫不足 → `InvariantViolationException("在庫不足です: 要求=%d, 在庫=%d")`。引き当て後 `stockQuantity == 0` なら OUT_OF_STOCK へ自動遷移
- `void cancelReservation(int quantity)` — USED: RESERVED→PUBLISHED。NEW: 在庫を戻し、OUT_OF_STOCK なら PUBLISHED へ
- `void confirmSale(int quantity, Instant now)` — USED: RESERVED→SOLD + `ListingSold` を積む。NEW: REMOVED なら `IllegalStateTransitionException("REMOVED な Listing の販売は確定できません")`、それ以外は状態遷移なし(在庫は reserve 時点で減っている)
- `requireStatus(expected, action)` の文言: `"%s は Status=%s のときのみ可能です(現在: %s)"`
- `transitionTo(next)` の文言: `"Listing のステータスを %s から %s へ変更することはできません"`
- `List<DomainEvent> pullEvents()`
- **不変条件**: USED が SOLD になったら不可逆 / USED の予約・売約は常に数量1 / NEW の在庫は0未満にならず、0で OUT_OF_STOCK / 遷移は許可テーブルのみ

**`ListingRepository`**
```java
void save(Listing listing);
Optional<Listing> findById(ListingId id);
List<Listing> findByPressingId(PressingId pressingId);
```

### 3.5 Ordering コンテキスト(`domain/ordering`)

**Enum `OrderStatus`**: `PENDING, PAID, SHIPPED, DELIVERED, CANCELLED`

| from | to |
|---|---|
| PENDING | PAID, CANCELLED |
| PAID | SHIPPED, CANCELLED |
| SHIPPED | DELIVERED |
| DELIVERED / CANCELLED | (終端) |

追加メソッド: `allowsShippingAddressChange()` = PENDING or PAID、`allowsBillingAddressChange()` = PENDING、`isTerminal()`

**ID**: `CartId(UUID)`(`generate()` のみ)、`OrderId(UUID)`

**イベント**: `OrderPlaced(OrderId orderId, Money totalAmount, Instant occurredAt)`、`ShippingAddressChanged(OrderId orderId, Instant occurredAt)`

**`CartLine`** record `(ListingId listingId, ConditionType conditionType, int quantity, Instant addedAt)`: `quantity < 1` → `InvariantViolationException`、`USED && quantity != 1` → `InvariantViolationException("Used Listing を含む CartLine の数量は常に 1 です: " + quantity)`

**`Cart`**(集約ルート、final class、DB永続化しない)
- フィールド: `CartId cartId`, `CustomerId customerId`, `List<CartLine> lines`
- `static Cart open(CartId, CustomerId)`
- `void addLine(ListingId, ConditionType, int quantity, Instant now)` — 同一 listingId の既存行を `removeIf` してから追加(上書きセマンティクス)。カート追加時点では在庫を予約しない
- `void removeLine(ListingId)`、`List<CartLine> lines()`(unmodifiable)、`boolean isEmpty()`

**`PressingSnapshot`** record(注文確定時点の凍結コピー)
```java
(String releaseTitle, String artistName, String labelName, String catalogNumber,
 String country, int pressYear, Format format, ConditionType conditionType,
 GoldmineGrade vinylGrade, GoldmineGrade sleeveGrade)
```
releaseTitle〜conditionType は non-null。`conditionType == USED` のときのみ両グレード non-null 必須(NEW では null)。

**`OrderLine`** record `(ListingId listingId, PressingSnapshot pressingSnapshot, Money unitPrice, int quantity)`: 全 non-null、`quantity < 1` → `InvariantViolationException`。`Money lineTotal()` = `unitPrice.multiply(quantity)`

**`Order`**(集約ルート、final class)
- フィールド: `OrderId orderId`, `CustomerId customerId`, `List<OrderLine> lines`(`List.copyOf`)、`Address shippingAddress`, `Address billingAddress`, `OrderStatus status`, `Instant placedAt`, `List<DomainEvent> pendingEvents`
- lines が null/empty → `InvariantViolationException("注文には最低 1 件の OrderLine が必要です")`。初期 status = PENDING
- `static Order place(OrderId, CustomerId, List<OrderLine>, Address shipping, Address billing, Instant placedAt)` — `OrderPlaced(orderId, totalAmount(), placedAt)` を積む
- `static Order reconstitute(..., OrderStatus status, Instant placedAt)`
- `Money totalAmount()` — 各 `lineTotal()` の合計
- `void changeShippingAddress(Address, Instant now)` — 不可なら `IllegalStateTransitionException("Status=%s の注文は配送先を変更できません(発送準備前のみ変更可)")`、成功で `ShippingAddressChanged` を積む
- `void changeBillingAddress(Address)` — 不可なら `"Status=%s の注文は請求先を変更できません(決済確定前のみ変更可)"`
- `void markPaid()` / `markShipped()` / `markDelivered()` / `cancel()` — 遷移テーブルに従う。文言 `"Order のステータスを %s から %s へ変更することはできません"`
- `pullEvents()`、`toString()` = `"Order{%s, status=%s, total=%s}"`

**`OrderRepository`**
```java
void save(Order order);
Optional<Order> findById(OrderId id);
List<Order> findByCustomerId(CustomerId customerId);   // placedAt 降順
List<Order> findAll();                                  // placedAt 降順(管理画面用)
```

**`OrderPlacementService`**(ドメインサービス、final class)
- コンストラクタ `(ListingRepository, ReleaseRepository, OrderRepository)`
- `Order placeOrder(Cart cart, Address shippingAddress, Address billingAddress, Instant now)`:
  1. `cart.isEmpty()` → `InvariantViolationException("空のカートから注文は作成できません")`
  2. 各 CartLine について: `listingRepository.findById`(無ければ `InvariantViolationException("Listing が見つかりません: " + id)`)→ `listing.reserve(quantity)` → 予約済みリストに記録 → `releaseRepository.findByPressingId`(無ければ `"Pressing を含む Release が見つかりません: "`)→ `release.findPressing`(無ければ `"Pressing が見つかりません: "`)→ `PressingSnapshot` を組み立て → `OrderLine` 生成
  3. 途中で `RuntimeException` が出たら、**予約済みListingをすべて `cancelReservation(quantity)` + `save` して補償ロールバック**し、例外を再throw
  4. `Order.place(OrderId.generate(), cart.customerId(), lines, shipping, billing, now)`
  5. 予約したListingを全 `save` → `orderRepository.save(order)` → return

### 3.6 Payment コンテキスト(`domain/payment`)

**Enum**
- `PaymentMethod`: `CREDIT_CARD, BANK_TRANSFER`
- `PaymentStatus`: `PENDING, CAPTURED, FAILED, REFUNDED`

  | from | to |
  |---|---|
  | PENDING | CAPTURED, FAILED |
  | CAPTURED | REFUNDED |
  | FAILED / REFUNDED | (終端) |

**ID**: `PaymentId(UUID)`

**イベント**: `PaymentCaptured(PaymentId, OrderId, Money amount, Instant occurredAt)`、`PaymentRefunded(PaymentId, Instant occurredAt)`

**`Payment`**(集約ルート、final class)
- フィールド: `PaymentId paymentId`, `OrderId orderId`, `Money amount`, `PaymentMethod method`(final)、`PaymentStatus status`, `Instant capturedAt`(nullable)、`pendingEvents`
- `static Payment initiate(PaymentId, OrderId, Money amount, PaymentMethod)` — status = PENDING
- `static Payment reconstitute(PaymentId, OrderId, Money, PaymentMethod, PaymentStatus, Instant capturedAt)`
- `void capture(Instant now)` — PENDING→CAPTURED、`capturedAt = now`、`PaymentCaptured` を積む
- `void fail()` — PENDING→FAILED
- `void refund(Instant now)` — CAPTURED→REFUNDED、`PaymentRefunded` を積む
- 文言 `"Payment のステータスを %s から %s へ変更することはできません"`
- `Optional<Instant> capturedAt()`、`pullEvents()`、equals/hashCode は paymentId

**`PaymentRepository`**
```java
void save(Payment payment);
Optional<Payment> findById(PaymentId id);
List<Payment> findByOrderId(OrderId orderId);
```

**`PaymentCaptureService`**(final class)
- コンストラクタ `(OrderRepository, PaymentRepository)`
- `Payment capturePayment(OrderId orderId, PaymentMethod method, Instant now)`: Order を取得(無ければ `InvariantViolationException("Order が見つかりません: " + orderId)`)→ `Payment.initiate(PaymentId.generate(), orderId, order.totalAmount(), method)` → `payment.capture(now)` → `order.markPaid()`(PAID済みなら遷移例外=二重決済拒否)→ `paymentRepository.save` → `orderRepository.save` → return payment
- 決済ゲートウェイ連携は行わず Capture は常に即時成功

### 3.7 Customer コンテキスト(`domain/customer`)

**Enum `CustomerRole`**: `CUSTOMER, ADMIN`
**ID**: `CustomerId(UUID)`、`EmailVerificationId(UUID)`(`generate()` のみ)

**`Email`** record `(String value)`: コンパクトコンストラクタで `value.trim().toLowerCase()` に正規化、`Pattern "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"` に不一致 → `IllegalArgumentException("email format is invalid: " + value)`。`toString()` = value

**ポート(interface)**
- `PasswordHasher`: `String hash(String rawPassword)`, `boolean matches(String rawPassword, String hash)`
- `EmailSender`: `void sendVerificationCode(Email to, String displayName, String verificationCode)`, `void sendRegistrationCompleted(Email to, String displayName)`
- `EmailDeliveryException extends RuntimeException`(`(String message, Throwable cause)`)

**`Customer`**(集約ルート、final class)
- フィールド: `CustomerId customerId`, `Email email`, `CustomerRole role`, `Instant registeredAt`(final)、`String passwordHash`, `String displayName`(可変)
- `static Customer register(CustomerId, Email, String passwordHash, String displayName, Instant registeredAt)` — **role は常に CUSTOMER**
- `static Customer reconstitute(CustomerId, Email, String passwordHash, String displayName, CustomerRole role, Instant registeredAt)` — ADMIN はこちら経由のみ
- `void changeDisplayName(String)` / `void changePasswordHash(String)` — non-blank
- ハッシュ化はドメインで行わない(ハッシュ済み文字列を受け取るだけ)
- Email の一意性は `CustomerRegistrationService` がリポジトリ経由で保証(DB UNIQUE 制約と二重の安全網)

**`EmailVerification`**(集約ルート、final class、仮登録)
- 定数: `SecureRandom RANDOM`、`MAX_ATTEMPTS = 5`
- フィールド: `EmailVerificationId`, `Email email`, `String passwordHash`, `String displayName`(final)、`String verificationCode`, `Instant expiresAt`, `int attemptCount`
- `static issue(EmailVerificationId, Email, String passwordHash, String displayName, Instant now, Duration ttl)` — 6桁コード生成(`String.format("%06d", RANDOM.nextInt(1_000_000))`)、`expiresAt = now.plus(ttl)`、attemptCount=0
- `static reconstitute(...)`(全7フィールド)
- `void reissueCode(Instant now, Duration ttl)` — コード再発行、期限更新、試行回数リセット
- `void confirm(String inputCode, Instant now)`:
  - 期限切れ → `InvariantViolationException("確認コードの有効期限が切れています。もう一度会員登録をやり直してください")`
  - `attemptCount >= MAX_ATTEMPTS` → `InvariantViolationException("確認コードの入力回数が上限に達しました。もう一度会員登録をやり直してください")`
  - 不一致 → `attemptCount++` してから `InvariantViolationException("確認コードが正しくありません")`

**リポジトリ**
```java
// CustomerRepository
void save(Customer c); Optional<Customer> findById(CustomerId); Optional<Customer> findByEmail(Email); boolean existsByEmail(Email);
// EmailVerificationRepository
void save(EmailVerification v);   // 同一emailの既存があれば上書き(upsert)
Optional<EmailVerification> findByEmail(Email);
void deleteByEmail(Email);
```

**`CustomerRegistrationService`**(final class、`(CustomerRepository, PasswordHasher)`)
- `Customer register(Email, String rawPassword, String displayName, Instant now)` — `assertEmailAvailable` → hash → `Customer.register(CustomerId.generate(), ...)` → save
- `Customer registerWithHashedPassword(Email, String passwordHash, String displayName, Instant now)`(平文を持ち回らないため分離)
- `void assertEmailAvailable(Email)` — 既存で `InvariantViolationException("このメールアドレスは既に登録されています: " + email)`

**`EmailVerificationService`**(final class、`(EmailVerificationRepository, CustomerRegistrationService, PasswordHasher, EmailSender)`)
- 定数 `CODE_TTL = Duration.ofMinutes(10)`
- フェーズ1 `void requestVerification(Email, String rawPassword, String displayName, Instant now)`: `assertEmailAvailable` → hash → `EmailVerification.issue(...)` → **先に `emailSender.sendVerificationCode`** → その後 `repository.save`(送信失敗時に仮登録レコードだけ残るのを防ぐ順序)。同一メールで再要求されたら新しいコードで上書き(前のコードは無効化)
- フェーズ2 `Customer confirmRegistration(Email, String inputCode, Instant now)`: `findByEmail`(無ければ `InvariantViolationException("確認コードの発行履歴が見つかりません。もう一度会員登録をやり直してください")`)→ `verification.confirm(...)`(`InvariantViolationException` を捕まえたら attemptCount を保存するため `save` してから再throw)→ `registerWithHashedPassword` → `deleteByEmail` → `sendRegistrationCompleted`(失敗しても `LOGGER.warn` で握りつぶし、本登録は成功扱い)

### 3.8 InMemory リポジトリ(`infrastructure/memory`)

`InMemoryReleaseRepository` / `InMemoryListingRepository` / `InMemoryOrderRepository` / `InMemoryPaymentRepository` / `InMemoryCustomerRepository` / `InMemoryEmailVerificationRepository`。
いずれも `public final class`、`Map<XxxId, Xxx> store = new LinkedHashMap<>()` を持つだけ。**Springアノテーションを付けない**(テストで手動 new)。`findByCustomerId`/`findAll` は placedAt 降順を守る。

### 3.9 テスト(純ユニット、`src/test/java/com/example/recordshop/domain/**`)

以下のクラス・メソッドを作る(メソッド名はこのまま使う)。

| クラス | メソッド |
|---|---|
| `catalog/ReleaseTest` | `addPressing_複数プレス版を追加できる` / `addPressing_製造国が2文字の国コードでないものは拒否される` / `addPressing_品番と製造国と製造年が同じPressingは拒否される` / `addPressing_製造年が違えば同じ品番でも登録できる` / `register_artworkUrlを指定して登録できる` / `register_artworkUrlが空文字の場合はnullとして扱われる` / `changeArtworkUrl_登録後にアートワークを設定_変更できる` / `changePressingArtworkUrl_指定したPressingのアートワークだけを変更できる` / `changePressingArtworkUrl_存在しないPressingIdを指定すると例外を投げる` |
| `inventory/ListingTest` | `used_publish後にreserveするとRESERVEDになる` / `used_数量2で予約しようとすると拒否される` / `used_Soldになったら再度予約や公開に戻せない` / `new_在庫を超える予約は拒否される` / `new_在庫が0になると自動的にOUT_OF_STOCKになる` / `new_キャンセルされると在庫が戻りステータスもPUBLISHEDに戻る` |
| `ordering/OrderTest` | `totalAmount_明細の合計になる` / `changeShippingAddress_PendingとPaidの間は変更できる` / `changeShippingAddress_発送後は変更できない` / `changeBillingAddress_決済確定後は変更できない` / `発送後は注文をキャンセルできない` |
| `ordering/OrderPlacementServiceTest` | `placeOrder_PressingSnapshotが確定時点の内容で複製される` / `placeOrder_一部のListingが公開されていない場合は全体をロールバックする` / `placeOrder_空のカートは拒否される` |
| `payment/PaymentTest` | `capture_PendingからCapturedになりcapturedAtが確定する` / `fail_Pendingから失敗にできる` / `fail_Captured済みは失敗にできない` / `refund_Capturedからのみ返金できる` / `capture_PaymentCapturedイベントが積まれる` |
| `payment/PaymentCaptureServiceTest` | `capturePayment_Order合計金額でCaptureされOrderがPAIDになる` / `capturePayment_存在しないOrderは拒否される` / `capturePayment_既にPAID済みのOrderは二重決済できない` |
| `customer/CustomerTest` | `register_一般会員はCUSTOMERロールになる` / `email_不正な形式は拒否される` / `changeDisplayName_表示名を変更できる` |
| `customer/CustomerRegistrationServiceTest` | `register_パスワードはハッシュ化されて平文のまま保持されない` / `register_同じEmailは二重登録できない` |
| `customer/EmailVerificationTest` | `issue_発行された確認コードは6桁の数字である` / `issue_有効期限はnowにttlを加えた時刻になる` / `confirm_正しいコードなら例外を投げない` / `confirm_誤ったコードは例外を投げattemptCountが増える` / `confirm_試行回数が上限に達すると正しいコードでも例外を投げる` / `confirm_有効期限切れなら正しいコードでも例外を投げる` / `reissueCode_コードと有効期限と試行回数がリセットされる` |
| `customer/EmailVerificationServiceTest` | `requestVerification_確認コードが発行されメールが送信される` / `requestVerification_登録済みのメールは例外を投げる` / `requestVerification_同じメールで2回呼ぶと1回目のコードが無効化される` / `confirmRegistration_正しいコードでCustomerが作成され仮登録は削除される` / `confirmRegistration_誤ったコードではCustomerを作成しない` / `confirmRegistration_仮登録が存在しない場合は例外を投げる` / `requestVerification_確認コードメールの送信に失敗したら仮登録を残さない` / `confirmRegistration_登録完了メールの送信に失敗しても本登録は成功する` |
| `shared/AddressTest` | `ISO3166_1_alpha2の2文字なら受け付ける` / `国名を書いた3文字以上はドメイン側で弾く` / `小文字や1文字も受け付けない` / `空文字はこれまでどおりblankとして弾く` / `有効な国コードはそのまま保持される` |
| `shared/IdentifiersTest` | `parse_正しいUUID文字列はそのまま変換される` / `parse_UUIDでない文字列はMalformedIdentifierException` / `parse_nullもMalformedIdentifierException` / `各IDのofも不正な文字列でMalformedIdentifierExceptionを投げる` |

- `EmailVerificationServiceTest` にはテスト内フェイクを持たせる: `FAKE_HASHER`(`"hashed:" + raw`)、`RecordingEmailSender`(送信内容を record で記録)、`FAILING_SENDER`(常に `EmailDeliveryException`)
- 合計 **60件**

## 4. 設計上の注意・落とし穴

- `Pressing` のコンストラクタをパッケージプライベートにし、「Pressing は Release を経由してのみ追加できる」を型で強制する
- `Money` は 0 を許容する(送料無料等の将来拡張のため)。負数のみ拒否
- `Cart.addLine` は「同じListingを2回追加したら数量加算」ではなく**上書き**。USEDは常に数量1
- `OrderPlacementService` の補償ロールバックは「例外を握りつぶさず再throw」する。トランザクション境界はここでは持たない(phase-3 の `CheckoutService` が持つ)
- `EmailVerificationService.requestVerification` の順序は「送信 → 保存」。逆にすると送信失敗時に仮登録だけ残り、再登録が「既に発行済み」扱いになる(元プロジェクトで実際に起きた不具合)
- ドメイン層に `org.springframework` を import しない。`@Service` も付けない

## 5. 完了条件

- `./mvnw test` が green(60件)
- `grep -r "org.springframework" src/main/java/com/example/recordshop/domain` が0件
- `./mvnw -q compile` が通る(Spring Boot 起動は phase-2 以降で確認)

## 6. コミット

- `Initial commit: Spring Boot骨格とMaven Wrapper`
- `フェーズ1: Catalog/Inventory/Ordering/Payment/Customer集約とドメイン単体テストを追加`
