# phase-4: サンプルデータ・Docker・README・GitHub公開(record-shop-ec-mybatis)

> このプロンプトを投げる前に、同じフォルダの `00-common.md` を読んで従うこと。
> 作業ディレクトリ: `<親フォルダ>/record-shop-ec-mybatis`(phase-3 完了状態)

## 1. このフェーズの目的

起動時にデモ用サンプルデータ(作品6件・顧客4名・注文4件)を投入する `SampleDataSeeder`、
ジャケット画像SVG、`robots.txt`、`Dockerfile` / `docker-compose.yml`、README を揃え、
**`docker compose up` だけで動くデモ環境**にしてから GitHub(private)へ push する。

## 2. 前提

- phase-3 の 93 テストが green

## 3. 仕様

### 3.1 `SampleDataSeeder`(`infrastructure/devdata`)

`@Component @Profile("!test") implements CommandLineRunner`。依存: `ReleaseRepository, ListingRepository, CustomerRepository, OrderRepository, PaymentRepository, OrderPlacementService, PaymentCaptureService, PasswordHasher`。
`releaseRepository.findAll()` が空でなければスキップ(冪等)。`@Profile("!test")` の理由: `@SpringBootTest` は ApplicationContext キャッシュで同じ H2 を使い回すため、Seeder が投入するデータが後続テストの前提件数を壊す。

**Release 6件**(artworkUrl は `/images/artwork/<slug>.svg`、Pressing の artworkUrl は `/images/artwork/pressings/<slug>-<cc>-<year>.svg`):

| # | title / artist / genres / year | Pressing(label / catalogNumber / country / year / format) | Listing |
|---|---|---|---|
| 1 | Blue Train / John Coltrane / Jazz, Hard Bop / 1957 | Blue Note `BLP 1577` US 1957(matrix `XSM`、LP/RPM_33/1)、Blue Note `CDP 7243 8 32097 2 5` US 1997(reissue、CD/NOT_APPLICABLE/1) | USED ¥58,000(VG+/VG)PUBLISHED、USED ¥32,000(GOOD+/GOOD)→ **SOLD**(注文Bとは別。reserve→confirmSale)、NEW ¥2,800 在庫5 PUBLISHED |
| 2 | The Dark Side of the Moon / Pink Floyd / Progressive Rock, Rock / 1973 | Harvest `SHVL 804` GB 1973、Harvest `0724347942911` GB 2011(reissue) | USED ¥55,000(NM/NM)、NEW ¥4,200 在庫10、NEW ¥4,500 在庫1 → `reserve(1)` で **OUT_OF_STOCK** |
| 3 | Thriller / Michael Jackson / Pop, R&B, Funk / 1982 | Epic `QE 38112` US 1982、Epic `1907581421` US 2018(reissue) | USED ¥12,000(VG/VG−)PUBLISHED、NEW ¥3,900 在庫8 **DRAFT のまま** |
| 4 | Nevermind / Nirvana / Grunge, Alternative Rock / 1991 | DGC `DGC-24425` US 1991 | USED ¥9,800(GOOD/GOOD+、sellerNote `盤面にスレ傷あり`)、NEW ¥15,000 在庫1 |
| 5 | A LONG VACATION / 大瀧詠一 / City Pop, Pop / 1981 | Sony/CBS `25AH 1444` JP 1981、Sony Music `SRJL-1` JP 2021(reissue) | USED ¥18,000(VG+/VG+)、NEW ¥4,800 在庫6 |
| 6 | Selected Ambient Works 85-92 / Aphex Twin / Electronic, Ambient / 1992 | Apollo/R&S `AMB 3922` GB 1992(LP/RPM_33/**2枚組**)、Warp `WARPCD092` GB 2006(reissue、CD) | USED ¥15,000(MINT/NM)、CD の Listing を **REMOVED** 状態で(`Listing.reconstitute` で直接生成 — REMOVED へ遷移する公開メソッドが無いための意図的例外) |

`ListingStatus` 6値のうち REMOVED 以外の5値を正規のビジネスメソッド経由で再現すること。

**Customer 4名**(全員 CUSTOMER、パスワード `Passw0rd!2024`、`registeredAt` は now の 4/3/2/1 日前):

| 表示名 | email | 住所 |
|---|---|---|
| 田中 花子 | `tanaka.hanako@example.com` | 150-0001 東京都 渋谷区 1-2-3 JP |
| 佐藤 次郎 | `sato.jiro@example.com` | 530-0001 大阪府 大阪市北区 梅田1-1-1 JP |
| 鈴木 美咲 | `suzuki.misaki@example.com` | 060-0001 北海道 札幌市中央区 北一条西2-3 JP |
| 高橋 健太 | `takahashi.kenta@example.com` | 810-0001 福岡県 福岡市中央区 天神2-2-2 JP |

**Order / Payment 4件**(`OrderPlacementService.placeOrder` でカートから作る):

| 注文 | 会員 | 内容 | 状態 |
|---|---|---|---|
| A | 田中 | Thriller USED ×1 | `Payment.initiate(BANK_TRANSFER)` のみ保存 → Order PENDING / Payment PENDING(銀行振込未払い) |
| B | 佐藤 | Selected Ambient Works 85-92 USED ×1 | `capturePayment(CREDIT_CARD)` → Listing `confirmSale(1)` で SOLD → Order PAID / Payment CAPTURED |
| C | 鈴木 | Dark Side NEW ×2 + A LONG VACATION USED ×1 | capture → USED のみ confirmSale → `markShipped()` → Order SHIPPED / Payment CAPTURED |
| D | 高橋 | Nevermind NEW ×1 | `Payment.initiate` → `capture` → `refund` をメモリ上で進めて **1回だけ save**(`PaymentRepository.save` が INSERT 専用のため)。Order は `markPaid()` → save → `cancel()` → save、Listing `cancelReservation(1)` → Order CANCELLED / Payment REFUNDED |

### 3.2 静的ファイル(`src/main/resources/static`)

- `robots.txt`: `User-agent: *` / `Disallow: /`
- `images/artwork/` に Release 用 SVG 6枚: `blue-train.svg`, `dark-side-of-the-moon.svg`, `thriller.svg`, `nevermind.svg`, `a-long-vacation.svg`, `selected-ambient-works-85-92.svg`
- `images/artwork/pressings/` に Pressing 用 SVG 11枚: `blue-train-us-1957.svg`, `blue-train-us-1997.svg`, `dark-side-of-the-moon-gb-1973.svg`, `dark-side-of-the-moon-gb-2011.svg`, `thriller-us-1982.svg`, `thriller-us-2018.svg`, `nevermind-us-1991.svg`, `a-long-vacation-jp-1981.svg`, `a-long-vacation-jp-2021.svg`, `selected-ambient-works-85-92-gb-1992.svg`, `selected-ambient-works-85-92-gb-2006.svg`
- SVG の内容は自作でよい(正方形 400×400、作品ごとに配色を変え、タイトル・アーティスト名・(Pressing 用は国/年)をテキストで描いた簡素なジャケット風。著作物の複製はしない)

### 3.3 `Dockerfile`(全文)

```dockerfile
# ---- Build stage ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# 依存関係の解決だけ先に行い、Dockerのレイヤーキャッシュを効かせる
# (pom.xml が変わらない限り、ソース変更のたびに毎回全依存をダウンロードし直さずに済む)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -q package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# root で実行しない
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/target/record-shop-ec-mybatis-*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

`.dockerignore`: `target/`, `out/`, `out-test/`, `.git/`, `.idea/`, `.vscode/`, `*.iml`, `*.log`

### 3.4 `docker-compose.yml`(全文)

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: recordshop_mybatis
      POSTGRES_USER: recordshop
      POSTGRES_PASSWORD: recordshop
    ports:
      - "5433:5432"
    volumes:
      - postgres-mybatis-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U recordshop -d recordshop_mybatis"]
      interval: 5s
      timeout: 5s
      retries: 10

  app:
    build: .
    ports:
      - "8081:8080"
    environment:
      DB_HOST: postgres
      DB_PORT: "5432"
      DB_NAME: recordshop_mybatis
      DB_USERNAME: recordshop
      DB_PASSWORD: recordshop
      # ローカル検証用の管理者アカウント(本番はSecrets Manager経由で別途設定)。
      # SampleDataSeederが投入するサンプル顧客アカウントとパスワードを揃えている。
      ADMIN_EMAIL: admin@example.com
      ADMIN_PASSWORD: Passw0rd!2024
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  postgres-mybatis-data:
```

ローカルの SES 送信を試す場合は `app.environment` に `MAIL_FROM_ADDRESS: {MAIL_FROM_ADDRESS}` と AWS 認証情報(`AWS_ACCESS_KEY_ID` 等、またはボリュームで `~/.aws` をマウント)を追加する。コミットには含めない。

### 3.5 `README.md`(章立てと内容)

1. `# record-shop-ec-mybatis` — 1段落: DDD学習用のレコード販売ECサイト。ドメイン層(5集約)・Web層(REST API・Thymeleaf・Spring Security)・永続化層(MyBatis)
2. `## スコープ` — ドメイン層 / Web層 / 永続化層の3項目
3. `## 実装のポイント(MyBatisで工夫した点)` — phase-2 の 4章 1〜6 をそのまま(該当クラスへの相対リンク付き)
4. `## ビルド・テスト` — `./mvnw test`、Docker 無しで H2 で通ること、テスト件数、`PageRenderingTest` の狙い
5. `## ローカル起動` — `docker compose up -d --build`(http://localhost:8081)、`curl -X POST http://localhost:8081/api/releases ...` の例、`docker compose down`。DBだけ起動して `./mvnw spring-boot:run` も可
6. `## ログイン` — 管理者 `admin@example.com` / `Passw0rd!2024`(ローカルのみ)、サンプル顧客4名(同パスワード)

### 3.6 GitHub 公開

```bash
git add -A && git commit -m "フェーズ4: サンプルデータ投入・Docker Compose・READMEを追加"
gh repo create {GITHUB_OWNER}/record-shop-ec-mybatis --private --source=. --remote=origin --push
```

## 4. 設計上の注意・落とし穴

- Seeder は phase-3 で実装した「決済確定 → confirmSale」「キャンセル → cancelReservation」と整合させる(注文B/C/D)
- 起動のたびに `schema.sql` で DROP→CREATE されるので Seeder は毎回走る。冪等チェックは「同一プロセス内での二重実行防止」の意味合い
- Docker イメージのビルドは `dependency:go-offline` で依存を先に落とす。ネットワークが遅い環境では初回に数分かかる
- `docker compose` のアプリポートは **8081**(ホスト)→ 8080(コンテナ)。DB は 5433 → 5432。ドキュメント・テスト仕様書では `http://localhost:8081` を使う

## 5. 完了条件

- `./mvnw test` green(93件。Seeder は test プロファイルで無効)
- `docker compose up -d --build` 後、`http://localhost:8081/catalog` に **PUBLISHED な Listing を持つ4作品**(Blue Train / The Dark Side of the Moon / Nevermind / A LONG VACATION)が表示される。Thriller(USED は注文Aで RESERVED、NEW は DRAFT)と Selected Ambient Works 85-92(USED は注文Bで SOLD、CD は REMOVED)は表示されない
- `admin@example.com` / `Passw0rd!2024` でログインし `/admin/orders` に注文4件(PENDING/PAID/SHIPPED/CANCELLED)が並ぶ
- `tanaka.hanako@example.com` でログインし `/orders` に自分の注文1件(PENDING)
- `gh repo view {GITHUB_OWNER}/record-shop-ec-mybatis` が private で見える

## 6. コミット

- `フェーズ4: 起動時にカタログ・顧客・注文・決済のサンプルデータを自動投入する機能を追加`
- `フェーズ4: robots.txt + noindexヘッダー、ジャケット画像SVGを追加`
- `フェーズ4: Dockerfile / Docker Compose / README を追加`
