# クラス図

ドメイン層(`domain/**`)の主要クラスを、コンテキストごとに示す。Web層・永続化層(MyBatis)は
含めない(対応関係は [02-er-diagram.md](02-er-diagram.md) を参照)。

## Catalog コンテキスト

```mermaid
classDiagram
    class Release {
        -ReleaseId releaseId
        -String title
        -String artistName
        -Set~String~ genres
        -int originalReleaseYear
        -List~Pressing~ pressings
        -String artworkUrl
        +register(...)$ Release
        +reconstitute(...)$ Release
        +addPressing(...) Pressing
        +findPressing(PressingId) Optional~Pressing~
        +changeArtworkUrl(String)
        +changePressingArtworkUrl(PressingId, String)
    }
    class Pressing {
        -PressingId pressingId
        -String labelName
        -String catalogNumber
        -String country
        -int pressYear
        -String matrixRunout
        -boolean reissue
        -Format format
        -String artworkUrl
        +identityKey() String
    }
    class Format {
        <<value object>>
        +MediaType mediaType
        +Speed speed
        +int discCount
    }
    Release "1" *-- "0..*" Pressing : 内包(CASCADE)
    Pressing --> Format

    note for Release "不変条件: 同一Release内でPressingは\n「品番+製造国+製造年」で一意\nPressingはReleaseを経由してのみ追加可能"
```

## Inventory コンテキスト

```mermaid
classDiagram
    class Listing {
        -ListingId listingId
        -PressingId pressingId
        -ConditionType conditionType
        -Money price
        -ListingStatus status
        -Integer stockQuantity
        -GoldmineGrade vinylGrade
        -GoldmineGrade sleeveGrade
        -String sellerNote
        +newCopy(...)$ Listing
        +usedCopy(...)$ Listing
        +publish(Instant)
        +reserve(int quantity)
        +cancelReservation(int quantity)
        +confirmSale(int quantity, Instant)
    }
    class ListingStatus {
        <<enumeration>>
        DRAFT
        PUBLISHED
        RESERVED
        SOLD
        OUT_OF_STOCK
        REMOVED
        +canTransitionTo(ListingStatus) boolean
    }
    Listing --> ListingStatus
    Listing ..> PressingId : ID参照のみ(別集約)

    note for Listing "不変条件:\nUsed が SOLD になったら不可逆\nUsed の予約・売約は常に数量1\nNew の在庫は0未満にならない、\n 0になったら自動的にOUT_OF_STOCK"
```

```mermaid
stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> PUBLISHED
    DRAFT --> REMOVED
    PUBLISHED --> RESERVED
    PUBLISHED --> OUT_OF_STOCK
    PUBLISHED --> REMOVED
    RESERVED --> PUBLISHED
    RESERVED --> SOLD
    OUT_OF_STOCK --> PUBLISHED
    OUT_OF_STOCK --> REMOVED
    SOLD --> [*]
    REMOVED --> [*]
    note right of SOLD : 終端・不可逆
```

## Ordering コンテキスト

```mermaid
classDiagram
    class Cart {
        -CartId cartId
        -CustomerId customerId
        -List~CartLine~ lines
        +open(...)$ Cart
        +addLine(ListingId, ConditionType, int, Instant)
        +removeLine(ListingId)
        +isEmpty() boolean
    }
    class CartLine {
        <<value object>>
        +ListingId listingId
        +ConditionType conditionType
        +int quantity
        +Instant addedAt
    }
    class Order {
        -OrderId orderId
        -CustomerId customerId
        -List~OrderLine~ lines
        -Address shippingAddress
        -Address billingAddress
        -OrderStatus status
        -Instant placedAt
        +place(...)$ Order
        +changeShippingAddress(Address, Instant)
        +changeBillingAddress(Address)
        +markPaid()
        +markShipped()
        +markDelivered()
        +cancel()
        +totalAmount() Money
    }
    class OrderLine {
        <<value object>>
        +ListingId listingId
        +PressingSnapshot pressingSnapshot
        +Money unitPrice
        +int quantity
        +lineTotal() Money
    }
    class PressingSnapshot {
        <<value object>>
        +String releaseTitle
        +String artistName
        +String labelName
        +String catalogNumber
        +String country
        +int pressYear
        +Format format
        +ConditionType conditionType
        +GoldmineGrade vinylGrade
        +GoldmineGrade sleeveGrade
    }
    class OrderStatus {
        <<enumeration>>
        PENDING
        PAID
        SHIPPED
        DELIVERED
        CANCELLED
        +allowsShippingAddressChange() boolean
        +allowsBillingAddressChange() boolean
    }
    class OrderPlacementService {
        -ListingRepository listingRepository
        -ReleaseRepository releaseRepository
        -OrderRepository orderRepository
        +placeOrder(Cart, Address, Address, Instant) Order
    }
    Cart "1" *-- "0..*" CartLine
    Order "1" *-- "1..*" OrderLine
    OrderLine --> PressingSnapshot
    Order --> OrderStatus
    OrderPlacementService ..> Cart : 参照
    OrderPlacementService ..> Listing : reserve/cancelReservation
    OrderPlacementService ..> Release : スナップショット生成元
    OrderPlacementService ..> Order : 生成

    note for Order "不変条件:\nShippingAddressはPENDING/PAIDの間のみ変更可\nBillingAddressはPENDINGの間のみ変更可\n(決済確定後は実質凍結)"
```

## Payment コンテキスト

```mermaid
classDiagram
    class Payment {
        -PaymentId paymentId
        -OrderId orderId
        -Money amount
        -PaymentMethod method
        -PaymentStatus status
        -Instant capturedAt
        +initiate(...)$ Payment
        +capture(Instant)
        +fail()
        +refund(Instant)
    }
    class PaymentStatus {
        <<enumeration>>
        PENDING
        CAPTURED
        FAILED
        REFUNDED
    }
    class PaymentCaptureService {
        -OrderRepository orderRepository
        -PaymentRepository paymentRepository
        +capturePayment(OrderId, PaymentMethod, Instant) Payment
    }
    Payment --> PaymentStatus
    Payment ..> OrderId : ID参照のみ(別集約)
    PaymentCaptureService ..> Order : markPaid()
    PaymentCaptureService ..> Payment : 生成・Capture

    note for Payment "歩く骨格フェーズでは実際の決済ゲートウェイ連携は行わず、\nCaptureは常に即時成功として扱う"
```

## Customer コンテキスト

```mermaid
classDiagram
    class Customer {
        -CustomerId customerId
        -Email email
        -String passwordHash
        -String displayName
        -CustomerRole role
        -Instant registeredAt
        +register(...)$ Customer
        +reconstitute(...)$ Customer
        +changeDisplayName(String)
        +changePasswordHash(String)
    }
    class CustomerRole {
        <<enumeration>>
        CUSTOMER
        ADMIN
    }
    class Email {
        <<value object>>
        +String value
    }
    class CustomerRegistrationService {
        -CustomerRepository customerRepository
        -PasswordHasher passwordHasher
        +register(Email, String, String, Instant) Customer
    }
    Customer --> CustomerRole
    Customer --> Email
    CustomerRegistrationService ..> Customer : Email重複チェック後に生成

    note for Customer "register()は常にCUSTOMER固定。\nADMINはreconstitute()経由でのみ復元される\n(=起動時のAdminAccountSeederのみが作成可能)"
```

## 共通値オブジェクト(domain/shared)

```mermaid
classDiagram
    class Money {
        <<value object>>
        +BigDecimal amount
        +Currency currency
        +add(Money) Money
        +multiply(int) Money
        +isGreaterThan(Money) boolean
    }
    class Address {
        <<value object>>
        +String recipientName
        +String postalCode
        +String prefecture
        +String city
        +String addressLine
        +String country
    }
```
