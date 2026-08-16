# phase-2: MyBatis永続化層(record-shop-ec-mybatis)

> このプロンプトを投げる前に、同じフォルダの `00-common.md` を読んで従うこと。
> 作業ディレクトリ: `<親フォルダ>/record-shop-ec-mybatis`(phase-1 完了状態)

## 1. このフェーズの目的

phase-1 のドメイン層を **MyBatis + PostgreSQL** で永続化する。`schema.sql` によるDDL、Mapper(XML + interface)、
リポジトリ実装、`application.yml`、H2 による結合テスト、楽観ロック競合テストまで揃える。

## 2. 前提

- phase-1 の 60 テストが green
- MyBatis の設計方針(4章)は「JPAとの比較実験」として意図的に手作業でやっている点が多い。自動化ライブラリ(MyBatis Generator、MyBatis-Plus 等)は使わない

## 3. 仕様

### 3.1 `src/main/resources/schema.sql`(全文。このまま作る)

```sql
-- record-shop-ec-mybatis のスキーマ定義。
-- 簡略化のため、起動のたびに作り直す(DROP→CREATE)。データの永続化は目的外。

DROP TABLE IF EXISTS payments CASCADE;
DROP TABLE IF EXISTS order_lines CASCADE;
DROP TABLE IF EXISTS orders CASCADE;
DROP TABLE IF EXISTS listings CASCADE;
DROP TABLE IF EXISTS release_genres CASCADE;
DROP TABLE IF EXISTS pressings CASCADE;
DROP TABLE IF EXISTS releases CASCADE;
DROP TABLE IF EXISTS customers CASCADE;
DROP TABLE IF EXISTS email_verifications CASCADE;

CREATE TABLE releases (
    id                    UUID PRIMARY KEY,
    title                 VARCHAR(255) NOT NULL,
    artist_name           VARCHAR(255) NOT NULL,
    original_release_year INTEGER      NOT NULL,
    artwork_url           VARCHAR(1000)
);

-- ジャンルはRelease自身が持つ値の集合であり、独立したエンティティではない。
CREATE TABLE release_genres (
    release_id UUID        NOT NULL REFERENCES releases (id) ON DELETE CASCADE,
    genre      VARCHAR(50) NOT NULL,
    PRIMARY KEY (release_id, genre)
);

-- Release集約内の親子。Releaseが消えればPressingも連動して消える(CASCADE)。
CREATE TABLE pressings (
    id             UUID PRIMARY KEY,
    release_id     UUID         NOT NULL REFERENCES releases (id) ON DELETE CASCADE,
    label_name     VARCHAR(255) NOT NULL,
    catalog_number VARCHAR(100) NOT NULL,
    country        VARCHAR(2)   NOT NULL,
    press_year     INTEGER      NOT NULL,
    matrix_runout  VARCHAR(255),
    reissue        BOOLEAN      NOT NULL,
    media_type     VARCHAR(20)  NOT NULL,
    speed          VARCHAR(20)  NOT NULL,
    disc_count     INTEGER      NOT NULL,
    artwork_url    VARCHAR(1000),
    CONSTRAINT uk_pressing_identity UNIQUE (release_id, catalog_number, country, press_year)
);

-- versionはMyBatisでは自動付与されないため、UPDATE文側で明示的にWHERE version = ?と
-- version = version + 1を書いて楽観ロックを手動実装する。
CREATE TABLE listings (
    id              UUID           PRIMARY KEY,
    pressing_id     UUID           NOT NULL,
    condition_type  VARCHAR(10)    NOT NULL,
    price_amount    NUMERIC(12, 2) NOT NULL,
    price_currency  VARCHAR(3)     NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    stock_quantity  INTEGER,
    vinyl_grade     VARCHAR(20),
    sleeve_grade    VARCHAR(20),
    seller_note     VARCHAR(1000),
    version         BIGINT         NOT NULL DEFAULT 0
);

-- customer_idはCustomer(別集約)への参照のため、listingsのpressing_idと同様
-- ただのUUID列として持たせる(FK・JOINは張らない)。住所は列展開して持つ。
CREATE TABLE orders (
    id                     UUID           PRIMARY KEY,
    customer_id            UUID           NOT NULL,
    status                 VARCHAR(20)    NOT NULL,
    placed_at              TIMESTAMP      NOT NULL,
    ship_recipient_name    VARCHAR(255)   NOT NULL,
    ship_postal_code       VARCHAR(20)    NOT NULL,
    ship_prefecture        VARCHAR(50)    NOT NULL,
    ship_city              VARCHAR(100)   NOT NULL,
    ship_address_line      VARCHAR(255)   NOT NULL,
    ship_country           VARCHAR(2)     NOT NULL,
    bill_recipient_name    VARCHAR(255)   NOT NULL,
    bill_postal_code       VARCHAR(20)    NOT NULL,
    bill_prefecture        VARCHAR(50)    NOT NULL,
    bill_city              VARCHAR(100)   NOT NULL,
    bill_address_line      VARCHAR(255)   NOT NULL,
    bill_country           VARCHAR(2)     NOT NULL
);

-- Order自身が持つ一部(値の集合)。PressingSnapshotは確定時点の複製であり
-- releases/pressingsとは無関係な独立した非正規化データなので、列をそのまま展開する。
CREATE TABLE order_lines (
    order_id             UUID           NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    listing_id           UUID           NOT NULL,
    release_title        VARCHAR(255)   NOT NULL,
    artist_name          VARCHAR(255)   NOT NULL,
    label_name           VARCHAR(255)   NOT NULL,
    catalog_number       VARCHAR(100)   NOT NULL,
    pressing_country     VARCHAR(2)     NOT NULL,
    press_year           INTEGER        NOT NULL,
    media_type           VARCHAR(20)    NOT NULL,
    speed                VARCHAR(20)    NOT NULL,
    disc_count           INTEGER        NOT NULL,
    condition_type       VARCHAR(10)    NOT NULL,
    vinyl_grade          VARCHAR(20),
    sleeve_grade         VARCHAR(20),
    unit_price_amount    NUMERIC(12, 2) NOT NULL,
    unit_price_currency  VARCHAR(3)     NOT NULL,
    quantity             INTEGER        NOT NULL
);

-- order_idはOrder(別集約)への参照のため、listings.pressing_idと同様ただのUUID列として持たせる。
CREATE TABLE payments (
    id               UUID           PRIMARY KEY,
    order_id         UUID           NOT NULL,
    amount_amount    NUMERIC(12, 2) NOT NULL,
    amount_currency  VARCHAR(3)     NOT NULL,
    method           VARCHAR(20)    NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    captured_at      TIMESTAMP
);

-- emailはCustomerRegistrationServiceが守るべき「システム全体で一意」という不変条件を、
-- DB制約としても二重に保証しておく。
CREATE TABLE customers (
    id             UUID         PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    display_name   VARCHAR(255) NOT NULL,
    role           VARCHAR(20)  NOT NULL,
    registered_at  TIMESTAMP    NOT NULL
);

-- 仮登録(会員登録の確認コード待ち)。confirmされて初めてcustomersへ本登録される。
-- customersとは別集約・別テーブルであり、FK等の直接参照は持たない。
-- emailにUNIQUE制約を付け、同一メールの仮登録は常に1件のみ(再登録時は上書き=upsert)。
CREATE TABLE email_verifications (
    id                 UUID         PRIMARY KEY,
    email              VARCHAR(255) NOT NULL UNIQUE,
    password_hash      VARCHAR(255) NOT NULL,
    display_name       VARCHAR(255) NOT NULL,
    verification_code  VARCHAR(6)   NOT NULL,
    expires_at         TIMESTAMP    NOT NULL,
    attempt_count      INTEGER      NOT NULL DEFAULT 0
);
```

Flyway / Liquibase は使わない。`spring.sql.init.mode=always` により**起動のたびに DROP→CREATE** される(データ永続化は目的外。ECSタスク再起動でもDBは初期化される)。

### 3.2 `src/main/resources/application.yml`(全文)

```yaml
# server.forward-headers-strategy はあえて使わない(=デフォルトのnone)。
# CloudFront(HTTPS)→ALB→Fargate(HTTP)構成ではALBがX-Forwarded-Protoを毎回上書きしてしまうため、
# 代わりにCloudFrontProtoFilter(infrastructure.web)でscheme/isSecureを直接上書きする。

spring:
  application:
    name: record-shop-ec-mybatis
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5433}/${DB_NAME:recordshop_mybatis}
    username: ${DB_USERNAME:recordshop}
    password: ${DB_PASSWORD:recordshop}
  sql:
    init:
      # 簡略化のため、起動のたびにschema.sqlでテーブルを作り直す(データは永続化しない)
      mode: always

mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-handlers-package: com.example.recordshop.infrastructure.mybatis
  configuration:
    map-underscore-to-camel-case: true
    # 発行されるSQLをログで確認できるようにしておく(N+1になっていないかの目視確認用)
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl

logging:
  level:
    com.example.recordshop.infrastructure.mybatis: debug

management:
  endpoints:
    web:
      exposure:
        # ALB/ECSのヘルスチェックが叩く /actuator/health のみ公開する
        include: health
  endpoint:
    health:
      show-details: never

app:
  admin:
    # 起動時に管理者アカウントを1件だけ起票する(AdminAccountSeeder)。
    # 未設定のままだと /admin/** に入れる会員が誰もいない状態になる。
    email: ${ADMIN_EMAIL:}
    password: ${ADMIN_PASSWORD:}
  mail:
    # 確認コード・登録完了メールの送信元(SESで送信検証済みのメールアドレスである必要がある)。
    # SESサンドボックス中は宛先側も検証済みである必要がある。
    from-address: ${MAIL_FROM_ADDRESS:no-reply@example.com}
    aws:
      region: ${AWS_SES_REGION:ap-northeast-1}
```

`application-*.yml`(プロファイル別ファイル)は作らない。環境変数一覧:

| 環境変数 | 既定値 | 用途 |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5433` / `recordshop_mybatis` | PostgreSQL接続 |
| `DB_USERNAME` / `DB_PASSWORD` | `recordshop` / `recordshop` | |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | 空 | 起動時の管理者作成(phase-3) |
| `MAIL_FROM_ADDRESS` | `no-reply@example.com` | SES送信元(phase-3) |
| `AWS_SES_REGION` | `ap-northeast-1` | |

### 3.3 `src/test/resources/application.properties`(全文)

```properties
# Dockerが無くても自動テストを回せるように、H2(PostgreSQL互換モード)を使う。
spring.datasource.url=jdbc:h2:mem:recordshop_mybatis;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.sql.init.mode=always

# SampleDataSeeder(infrastructure.devdata)は @Profile("!test") のため、
# test プロファイルを有効にしてテスト実行時は起動時サンプルデータ投入をスキップする。
spring.profiles.active=test
```

### 3.4 `UuidTypeHandler`(`infrastructure/mybatis`)

```java
@MappedTypes(UUID.class)
public class UuidTypeHandler extends BaseTypeHandler<UUID> {
    // setNonNullParameter: ps.setObject(i, parameter)
    // getNullableResult(ResultSet, String/int) / (CallableStatement, int): (UUID) rs.getObject(...)
}
```
MyBatis は UUID の TypeHandler を持たず、無いと resultMap 構築時に `No typehandler found for property null` で起動失敗する。`mybatis.type-handlers-package` で自動登録される。

### 3.5 Row record(`infrastructure/mybatis`、DBの1行を表す record)

```java
record ReleaseRow(UUID id, String title, String artistName, int originalReleaseYear, String artworkUrl)
record GenreRow(UUID releaseId, String genre)
record PressingRow(UUID id, UUID releaseId, String labelName, String catalogNumber, String country,
                   int pressYear, String matrixRunout, boolean reissue,
                   String mediaType, String speed, int discCount, String artworkUrl)
record ListingRow(UUID id, UUID pressingId, String conditionType, BigDecimal priceAmount, String priceCurrency,
                  String status, Integer stockQuantity, String vinylGrade, String sleeveGrade, String sellerNote, long version)
record OrderRow(UUID id, UUID customerId, String status, Instant placedAt,
                String shipRecipientName, String shipPostalCode, String shipPrefecture, String shipCity, String shipAddressLine, String shipCountry,
                String billRecipientName, String billPostalCode, String billPrefecture, String billCity, String billAddressLine, String billCountry)
record OrderLineRow(UUID orderId, UUID listingId, String releaseTitle, String artistName, String labelName, String catalogNumber,
                    String pressingCountry, int pressYear, String mediaType, String speed, int discCount, String conditionType,
                    String vinylGrade, String sleeveGrade, BigDecimal unitPriceAmount, String unitPriceCurrency, int quantity)
record PaymentRow(UUID id, UUID orderId, BigDecimal amountAmount, String amountCurrency, String method, String status, Instant capturedAt)
record CustomerRow(UUID id, String email, String passwordHash, String displayName, String role, Instant registeredAt)
record EmailVerificationRow(UUID id, String email, String passwordHash, String displayName, String verificationCode, Instant expiresAt, int attemptCount)
```

### 3.6 Mapper インターフェース(`@Mapper`)と XML(`src/main/resources/mapper/*.xml`)

XML は6本(`ReleaseMapper.xml` `ListingMapper.xml` `OrderMapper.xml` `PaymentMapper.xml` `CustomerMapper.xml` `EmailVerificationMapper.xml`)。DOCTYPE `mybatis-3-mapper.dtd`、namespace は Mapper の FQCN。resultMap は `<constructor>` で record の正準コンストラクタにマッピングする。

| Mapper | メソッド |
|---|---|
| `ReleaseMapper` | `insertRelease(ReleaseRow)`, `insertGenre(@Param("releaseId") UUID, @Param("genre") String)`, `insertPressing(PressingRow)`, `updateRelease(ReleaseRow)`, `deleteGenresByReleaseId(UUID)`, `deletePressingsByReleaseId(UUID)`, `ReleaseRow selectReleaseById(UUID)`, `List<GenreRow> selectGenresByReleaseId(UUID)`, `List<PressingRow> selectPressingsByReleaseId(UUID)`, `List<ReleaseRow> selectAllReleases()`, `List<GenreRow> selectAllGenres()`, `List<PressingRow> selectAllPressings()`, `UUID selectReleaseIdByPressingId(UUID)`(resultType `java.util.UUID`) |
| `ListingMapper` | `insert(ListingRow)`, `ListingRow selectById(UUID)`, `List<ListingRow> selectByPressingId(UUID)`, `int update(ListingRow)`(戻り値0で楽観ロック競合) |
| `OrderMapper` | `insertOrder(OrderRow)`, `insertOrderLine(OrderLineRow)`, `updateOrder(OrderRow)`(status + 住所12列のみ。order_lines は不変)、`OrderRow selectOrderById(UUID)`, `List<OrderLineRow> selectOrderLinesByOrderId(UUID)`, `List<OrderRow> selectOrdersByCustomerId(UUID)`(`ORDER BY placed_at DESC`), `List<OrderRow> selectAllOrders()`(同), `List<OrderLineRow> selectOrderLinesByOrderIds(@Param("orderIds") List<UUID>)`(`<foreach>` IN句) |
| `PaymentMapper` | `insert(PaymentRow)`, `PaymentRow selectById(UUID)`, `List<PaymentRow> selectByOrderId(UUID)`(UPDATEなし) |
| `CustomerMapper` | `insert(CustomerRow)`, `CustomerRow selectById(UUID)`, `CustomerRow selectByEmail(String)`, `int countByEmail(String)`(UPDATEなし) |
| `EmailVerificationMapper` | `insert(EmailVerificationRow)`, `updateByEmail(EmailVerificationRow)`(`WHERE email = #{email}`、idも含めて上書き), `EmailVerificationRow selectByEmail(String)`, `int countByEmail(String)`, `deleteByEmail(String)` |

**楽観ロックの要(`ListingMapper.xml`)**:
```sql
UPDATE listings
SET condition_type = #{conditionType}, price_amount = #{priceAmount}, price_currency = #{priceCurrency},
    status = #{status}, stock_quantity = #{stockQuantity}, vinyl_grade = #{vinylGrade},
    sleeve_grade = #{sleeveGrade}, seller_note = #{sellerNote},
    version = version + 1
WHERE id = #{id} AND version = #{version}
```

**N+1回避の IN 句(`OrderMapper.xml`)**:
```xml
WHERE order_id IN
<foreach item="orderId" collection="orderIds" open="(" separator="," close=")">#{orderId}</foreach>
```

**resultMap の javaType 指定(重要)**: record の primitive フィールドには **`_int` / `_long` / `_boolean`** を使う(例: `original_release_year javaType="_int"`、`version javaType="_long"`、`reissue javaType="_boolean"`)。`int`/`long`/`boolean` という別名はラッパー型を指すため、正準コンストラクタが見つからず `NoSuchMethodException` になる。`Integer` 型のフィールド(`stock_quantity`)は `java.lang.Integer`、`Instant` は `java.time.Instant`、`BigDecimal` は `java.math.BigDecimal`、UUID は `java.util.UUID`。

### 3.7 リポジトリ実装(`@Repository`、`infrastructure/mybatis`)

| クラス | 振る舞い |
|---|---|
| `MyBatisReleaseRepository` | `save`(`@Transactional`): `selectReleaseById == null` で新規判定。新規→`insertRelease`、既存→`updateRelease` + `deleteGenresByReleaseId` + `deletePressingsByReleaseId`。その後 genres/pressings を全 re-insert(dirty checking が無いため子コレクションは全delete→re-insert で最新化)。`findById`(readOnly): 本体・genres(`LinkedHashSet`)・pressings を別SELECTで取得し `Release.reconstitute`。`findByPressingId`: `selectReleaseIdByPressingId` → `findById`。`findAll`: **`selectAllReleases` + `selectAllGenres` + `selectAllPressings` の3回のSELECTのみ**で `HashMap<UUID, ...>` にJava側でグルーピング(N+1回避) |
| `MyBatisListingRepository` | `ThreadLocal<Map<ListingId, Long>> loadedVersions`(`withInitial(HashMap::new)`)。`findById`/`findByPressingId` で読み込んだ version を記録。`save`(`@Transactional`): 記録が無ければ新規 → `insert(version=0)` して記録。あれば `update(row with knownVersion)`、更新件数 0 なら **`org.springframework.dao.OptimisticLockingFailureException("Listing " + id + " は他のトランザクションによって更新されています(楽観ロック競合)")`** を投げる。成功したら記録を `knownVersion + 1` に進める。既知の簡略化として「ThreadLocal はリクエスト完了時に自動クリアされない。本番なら Filter/`HandlerInterceptor#afterCompletion` でクリアが必要」をコメントに残す |
| `MyBatisOrderRepository` | `save`(`@Transactional`): `selectOrderById == null` で新規判定。新規→`insertOrder` + 各 `insertOrderLine`、既存→`updateOrder` のみ。`findById`: 本体 + `selectOrderLinesByOrderId`。`findByCustomerId`/`findAll`: 注文一覧を取り、orderIds で **`selectOrderLinesByOrderIds` を1回**呼んで `HashMap<UUID, List<OrderLine>>` にグルーピング |
| `MyBatisPaymentRepository` | `save` は `insert` のみ(INSERT専用)。`findById`/`findByOrderId` → `Payment.reconstitute` |
| `MyBatisCustomerRepository` | `save` は `insert` のみ。`existsByEmail` = `countByEmail > 0`。`Customer.reconstitute` |
| `MyBatisEmailVerificationRepository` | `save`: `countByEmail > 0` なら `updateByEmail`、無ければ `insert`(**`ON CONFLICT` を使わない**のは H2 PostgreSQL互換モードでも動かすため)。`findByEmail` / `deleteByEmail` |

Enum ↔ 文字列は `valueOf`/`name()`、`Currency` は `Currency.getInstance(code)`。

### 3.8 テスト(`src/test/java/com/example/recordshop/infrastructure/mybatis`)

`@SpringBootTest @Transactional`(H2、各テスト後ロールバック)。**楽観ロック競合テストのみ `@Transactional` を付けない**。

| クラス | メソッド |
|---|---|
| `MyBatisReleaseRepositoryTest` | `save_findById_で保存した集約が同じ内容で復元できる` / `save_changeArtworkUrl後に保存すると変更内容が永続化される` / `findByPressingId_でPressingを含むReleaseを検索できる` / `findAll_で複数Releaseのgenres_pressingsが取り違えなく組み立てられる` |
| `MyBatisListingRepositoryTest` | `save_findById_で保存したListingが同じ内容で復元できる` / `状態遷移を保存してfindByIdで読み直すと最新状態が反映されている` |
| `MyBatisListingOptimisticLockingTest` | `同時に同じUsedListingを予約すると片方だけ成功しもう片方は楽観ロック競合で失敗する` — 2スレッド + `CyclicBarrier` で同時に `findById → reserve(1) → save`(各スレッドは `TransactionTemplate` で独立トランザクション)。片方成功・片方 `OptimisticLockingFailureException`、最終状態は RESERVED(二重販売なし) |
| `MyBatisOrderRepositoryTest` | `save_findById_で保存したOrderが住所_明細ともに同じ内容で復元できる` / `findByCustomerId_で複数注文のOrderLineが取り違えなくグルーピングされる` / `findAll_で異なる顧客の注文も含めて全件_明細を取り違えなく取得できる` |
| `MyBatisPaymentRepositoryTest` | `save_findById_でCaptureされたPaymentがcapturedAtまで含めて復元できる` / `save_findById_でPENDINGのPaymentはcapturedAtが空で復元される` |
| `MyBatisCustomerRepositoryTest` | `save_findById_で保存したCustomerが同じ内容で復元できる` / `existsByEmail_登録済みのEmailはtrue_未登録はfalse` |
| `MyBatisEmailVerificationRepositoryTest` | `save_findByEmail_で保存した仮登録が同じ内容で復元できる` / `save_同じEmailで2回保存すると上書きされる` / `deleteByEmail_削除後はfindByEmailが空になる` |

合計 **17件**(累計 77件)。`@SpringBootTest` はまだ Web層が無いので起動できる最小構成でよい(phase-3 で Security 等が加わっても壊れないよう、テストは Web に依存しない)。

## 4. 設計上の注意・落とし穴(MyBatisならではの工夫点。README にも後で書く)

1. **UUID型の変換**: TypeHandler 自作が必須(3.4)
2. **プリミティブ型の javaType 別名**: `_int` / `_long` / `_boolean`(3.6)
3. **N+1の回避**: 集約の自動組み立てが無いため、`findAll()` / `findByCustomerId()` は「全件(またはIN句で対象分)を1回のSELECTで取ってJava側でグルーピング」する。Release / Order 双方に同じ原則
4. **楽観ロック**: 永続化コンテキストが無いため `ThreadLocal` で「読み込んだ時点の version」を手動で覚える。`UPDATE ... WHERE id=? AND version=?` の更新件数0で競合と判定
5. **dirty checking が無い**: `save()` は SELECT で既存有無を判定して INSERT/UPDATE を振り分け、子コレクションは全delete→re-insert。元プロジェクトではこの実装漏れが「既存Releaseへのpressing追加」「決済確定によるOrderステータス更新」をAPI経由で動かして初めて主キー重複エラーとして発覚した(ユニットテストだけでは気づけない典型例)。**リポジトリテストで「保存→変更→再保存→読み直し」を必ず含める**
6. **競合例外は Spring 基底の `OptimisticLockingFailureException`** を投げる。上位層(phase-3 の `ApiExceptionHandler` / `CheckoutService`)が特定の永続化技術に依存せずに捕捉できる
7. `map-underscore-to-camel-case: true` は resultType 用。`<constructor>` を使う resultMap では column 名を明示する
8. H2 の `MODE=PostgreSQL` で `UUID` 型・`NUMERIC`・`TIMESTAMP` は PostgreSQL と同じ挙動になる。`ON CONFLICT` 等の方言は避ける

## 5. 完了条件

- `./mvnw test` が green(累計 77件)
- Docker で PostgreSQL を起動し、アプリが起動して9テーブルが作られる:
  ```bash
  docker run -d --name rs-pg -e POSTGRES_DB=recordshop_mybatis -e POSTGRES_USER=recordshop -e POSTGRES_PASSWORD=recordshop -p 5433:5432 postgres:16-alpine
  ./mvnw spring-boot:run
  ```
  (phase-4 で `docker-compose.yml` に置き換える)
- 起動ログの MyBatis SQL(StdOutImpl)で `findAll` 系が SELECT 3回以内であることを目視確認

## 6. コミット

- `フェーズ2: schema.sqlとMyBatis永続化層(Catalog/Inventory)を追加`
- `フェーズ2: Ordering/Payment/Customer/EmailVerificationのMyBatis実装と結合テストを追加`
- `フェーズ2: Listingの楽観ロック競合テストを追加`
