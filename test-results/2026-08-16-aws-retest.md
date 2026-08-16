# システムテスト再実施記録: 2026-08-16(AWS環境・修正版)

[2026-08-16-aws.md](2026-08-16-aws.md) で検出した不具合8件の修正を `record-shop-ec-mybatis` に反映し、
AWS環境へ再デプロイしたうえで全68ケースを通しで再実施した記録。

## 1. 実施サマリ

| 項目 | 内容 |
|---|---|
| 実施日 | 2026-08-16(UTC 02:20〜03:00) |
| 実施環境 | **AWS(CloudFront経由)** |
| `{ベースURL}` | `https://d10qc58jhjv0fq.cloudfront.net` |
| AlbDirectUrl | `http://Record-Recor-KImzb7yjyRT3-313428848.ap-northeast-1.elb.amazonaws.com` |
| 対象コミット | `record-shop-ec-mybatis` = `701dd9d`(前回は `a226476`) |
| デプロイ | `npx cdk deploy RecordShopEcMybatisCdkStack` を実行(Deployment time: 285秒) |

### 結果内訳(前回との比較)

| 判定 | 前回 | 今回 | 差分 |
|---|---|---|---|
| Pass | 50 | **62** | +12 |
| Pass(条件付き) | 5 | 5 | ±0 |
| **Fail** | 11 | **0** | -11 |
| Blocked | 2 | 1 | -1 |
| 合計 | 68 | 68 | |

**前回Failだった11ケースはすべて解消した。** 一方で、修正によって新しく表面化した問題を1件検出している([不具合#9](#不具合9-売り切れた商品を購入しようとしたときの扱い))。

| カテゴリ | Pass | 条件付き | Fail | Blocked | 計 |
|---|---|---|---|---|---|
| PUB(公開機能) | 17 | 1 | 0 | 1 | 19 |
| MEM(会員機能) | 9 | 1 | 0 | 0 | 10 |
| ADM(管理者機能) | 14 | 2 | 0 | 0 | 16 |
| AUTH(権限制御) | 10 | 0 | 0 | 0 | 10 |
| CONC(並行実行) | 1 | 1 | 0 | 0 | 2 |
| PAY(決済) | 4 | 0 | 0 | 0 | 4 |
| INFRA(インフラ) | 7 | 0 | 0 | 0 | 7 |

---

## 2. 前回Failだったケースの再確認

| No | 前回 | 今回 | 実測 |
|---|---|---|---|
| ST-PUB-003 | Fail | **Pass** | DRAFTの出品しか持たない `Test Album` は `/catalog` に表示されない。購入可能な出品を持たない `Thriller` / `Selected Ambient Works 85-92` も一覧から消えた |
| ST-PUB-005 | Fail | **Pass** | `/catalog/999999` → **404**(前回400)。形式が正しい未存在UUIDも404 |
| ST-PUB-010 | Fail | **Pass(条件付き)** | 未検証アドレスでも500にならず「確認コードのメールを送信できませんでした…」を表示。仮登録も残らない(再送信すると「発行履歴が見つかりません」) |
| ST-PUB-017 | Fail | **Pass** | 「確認コードが正しくありません」を表示(前回は500の空レスポンス) |
| ST-PUB-018 | Fail | **Pass** | 発行から10分経過後に正しいコードを送信 → 「確認コードの有効期限が切れています…」を表示 |
| ST-PUB-019 | Fail | **Pass** | 誤コード5回後、6回目に正しいコードを送っても「確認コードの入力回数が上限に達しました…」を表示し、本登録もされない |
| ST-MEM-002 | Fail | **Pass** | `listingId=nonexistent-id` でも `/catalog` へリダイレクトし「指定された商品が見つかりませんでした」を表示 |
| ST-CONC-001 | Fail | **Pass(条件付き)** | 500が解消。片方のみ注文確定、もう片方は `/cart` へ戻り案内を表示。ただし文言が内部メッセージ(不具合#9) |
| ST-CONC-002 | Fail | **Pass** | 在庫5に対し2セッション同時購入で**両方成功**、在庫5→3。リトライが機能している |
| ST-PAY-001 | Fail | **Pass** | 注文詳細に「お支払い状況」と `CAPTURED` が表示される |
| ST-PAY-004 | Fail | **Pass** | `GET /api/payments/nonexistent-id` → **404**(前回400) |

前回Blockedだった `ST-INFRA-006`(CDKデプロイ)も、今回は実際に `npx cdk deploy` を実行し、
Outputs に `ServiceUrl` と `AlbDirectUrl` が出力されることを確認したため **Pass** としている。

---

## 3. 全ケースの結果

### 3.1 公開機能テスト(PUB)

| No | 結果 | 実施メモ |
|---|---|---|
| ST-PUB-001 | Pass | `GET /` → 302 `/catalog` |
| ST-PUB-002 | Pass | 購入可能な出品を持つ4作品が表示 |
| ST-PUB-003 | Pass | DRAFTのみの作品は非表示 |
| ST-PUB-004 | Pass | 2プレス版それぞれにコンディション・価格・在庫・カート追加フォーム |
| ST-PUB-005 | Pass | 404 |
| ST-PUB-006 | Blocked | 「在庫0のPUBLISHED Listing」は実装上作れない(前回と同じ。[補足](#補足-st-pub-006-の前提条件は依然として成立しない))。ただし在庫0での出品登録は、JSONではなく作品詳細画面の「出品の登録に失敗しました: initialStock must be >= 1: 0」に改善済み |
| ST-PUB-007 | Pass | 302 `/catalog`、ナビが会員向け表示に |
| ST-PUB-008 | Pass | 302 `/login?error` |
| ST-PUB-009 | Pass | 同上 |
| ST-PUB-010 | Pass(条件付き) | 仕様書の宛先 `newuser001@example.com` はSESサンドボックス未検証のため送信自体ができず確認コード画面へは進まないが、500ではなく案内を表示。SES検証済み宛先では仕様書どおり `/register/confirm` へリダイレクト |
| ST-PUB-011 | Pass | 「パスワードは8文字以上で入力してください」 |
| ST-PUB-012 | Pass | 「メールアドレスの形式が正しくありません」 |
| ST-PUB-013 | Pass | 「表示名を入力してください」 |
| ST-PUB-014 | Pass | 「このメールアドレスは既に登録されています」 |
| ST-PUB-015 | Pass | コードが `573041` → `522716` に再発行され、旧コードは「確認コードが正しくありません」で拒否 |
| ST-PUB-016 | Pass | 302 `/login?registered`、以後ログイン可能、仮登録も削除 |
| ST-PUB-017 | Pass | 「確認コードが正しくありません」 |
| ST-PUB-018 | Pass | 「確認コードの有効期限が切れています。もう一度会員登録をやり直してください」 |
| ST-PUB-019 | Pass | 「確認コードの入力回数が上限に達しました…」、正しいコードでも本登録されない |

### 3.2 会員機能テスト(MEM)

| No | 結果 | 実施メモ |
|---|---|---|
| ST-MEM-001 | Pass | `/cart` へリダイレクトし対象商品が追加される |
| ST-MEM-002 | Pass | 302 `/catalog` +「指定された商品が見つかりませんでした」 |
| ST-MEM-003 | Pass | 対象明細がカートから消える |
| ST-MEM-004 | Pass | 4,200 + 4,800×2 = 13,800 |
| ST-MEM-005 | Pass | 302 `/login` |
| ST-MEM-006 | Pass(条件付き) | `country=JP` で注文確定。仕様書の `country=Japan` は500ではなく「国コードはISO 3166-1 alpha-2形式の大文字2文字で入力してください(例: JP)」の案内に改善(仕様書の入力値は依然として誤り) |
| ST-MEM-007 | Pass | 302 `/cart` +「カートが空です」 |
| ST-MEM-008 | Pass | 自分の注文のみ表示 |
| ST-MEM-009 | Pass | 注文番号・日時・ステータス・Pressingスナップショットを表示 |
| ST-MEM-010 | Pass | 404 |

### 3.3 管理者機能テスト(ADM)

| No | 結果 | 実施メモ |
|---|---|---|
| ST-ADM-001 | Pass | 全Releaseとプレス数 |
| ST-ADM-002 | Pass | `Test Album` 登録、genresまで反映 |
| ST-ADM-003 | Pass | 「タイトルを入力してください」 |
| ST-ADM-004 | Pass(条件付き) | 仕様書の `mediaType=VINYL` / `speed=33` は列挙子に存在せず400(仕様書側の誤り)。`LP` / `RPM_33` / `country=JP` なら正常 |
| ST-ADM-005 | Pass(条件付き) | `country=Japan` は500から「国コードはISO 3166-1 alpha-2形式…」の画面表示に改善。`pressYear=1700` も画面に留まりメッセージ表示。ただし型変換エラー(`pressYear=abc`)は依然400 |
| ST-ADM-006 | Pass | DRAFT・在庫5で作成 |
| ST-ADM-007 | Pass | DRAFT・USED(1点限り)で作成 |
| ST-ADM-008 | Pass | DRAFT→PUBLISHED、`/catalog` に即時反映 |
| ST-ADM-009 | Pass | 「アートワークを更新しました」、一覧・詳細に反映 |
| ST-ADM-010 | Pass | プレス版個別の画像が反映 |
| ST-ADM-011 | Pass | 全顧客の注文を新しい順に表示 |
| ST-ADM-012 | Pass | PENDING→PAID |
| ST-ADM-013 | Pass | PAID→SHIPPED |
| ST-ADM-014 | Pass | SHIPPED→DELIVERED |
| ST-ADM-015 | Pass | PENDING→CANCELLED |
| ST-ADM-016 | Pass | 「DELIVERED から PAID へ変更することはできません」、状態は不変 |

### 3.4 権限制御テスト(AUTH)

| No | 結果 | 実測 |
|---|---|---|
| ST-AUTH-001 | Pass | 匿名 `/catalog` → 200 |
| ST-AUTH-002 | Pass | 匿名 `/cart` → 302 `/login` |
| ST-AUTH-003 | Pass | 匿名 `/admin/releases` → 302 `/login` |
| ST-AUTH-004 | Pass | CUSTOMER `/catalog` → 200 |
| ST-AUTH-005 | Pass | CUSTOMER `/orders` → 200 |
| ST-AUTH-006 | Pass | CUSTOMER `/admin/releases` → 403 |
| ST-AUTH-007 | Pass | ADMIN `/catalog` → 200 |
| ST-AUTH-008 | Pass | ADMIN `/cart` → 200 |
| ST-AUTH-009 | Pass | ADMIN `/admin/orders` → 200 |
| ST-AUTH-010 | Pass | 匿名 `GET /api/releases/{id}` → 200 |

### 3.5 並行実行・在庫競合テスト(CONC)

| No | 結果 | 実施メモ |
|---|---|---|
| ST-CONC-001 | Pass(条件付き) | 在庫1のUSED出品に2セッションから同時チェックアウト。片方は `/orders/{id}` へ、もう片方は `/cart` へ戻り**500は発生しない**。注文は1件のみで二重販売なし、在庫も売り切れに遷移。ただし表示されるメッセージが仕様書の「他の注文と同時に処理されたため確定できませんでした。もう一度お試しください。」ではなく内部メッセージ(不具合#9) |
| ST-CONC-002 | Pass | 在庫5に対し2セッション同時購入で**両方とも注文確定**、在庫5→3、エラーなし |

### 3.6 決済フローテスト(PAY)

| No | 結果 | 実施メモ |
|---|---|---|
| ST-PAY-001 | Pass | 注文詳細に「お支払い状況」表と `CAPTURED` を表示 |
| ST-PAY-002 | Pass | 201 Created、`status=CAPTURED`、注文もPAIDへ |
| ST-PAY-003 | Pass | 200 |
| ST-PAY-004 | Pass | 404 |

### 3.7 インフラ・非機能テスト(INFRA)

| No | 結果 | 実施メモ |
|---|---|---|
| ST-INFRA-001 | Pass | `http://` → 301 → `https://` |
| ST-INFRA-002 | Pass | 200 |
| ST-INFRA-003 | Pass | ALB直接アクセスで200 |
| ST-INFRA-004 | Pass | `/actuator/health` → 200 |
| ST-INFRA-005 | Pass | `aws ecs update-service --force-new-deployment` 実行後、同一セッションで `/cart` → 302 `/login`(カート・ログインセッションともに消失)。作品5件→4件となりテスト投入分は消えてサンプルデータのみに戻った |
| ST-INFRA-006 | Pass | `npx cdk deploy RecordShopEcMybatisCdkStack` を実行。Outputs に `ServiceUrl` と `AlbDirectUrl` が出力された(Deployment time: 285秒) |
| ST-INFRA-007 | Pass | SES検証済みアドレス宛に確認コードメール3通・登録完了メール1通を送信。`MessageRejectedException` は発生せず、`/register/confirm` へ正常に遷移し本登録まで完了 |

---

## 4. 今回検出した不具合

### 不具合#9: 売り切れた商品を購入しようとしたときの扱い

- **対象ケース**: ST-CONC-001
- **期待**: 「他の注文と同時に処理されたため確定できませんでした。もう一度お試しください。」
- **実際**: `Listing のステータスを RESERVED から RESERVED へ変更することはできません`
- **再現手順**(同時実行は不要): 在庫1のUSED出品を会員Aが購入して売り切れにしたあと、
  同じ出品をカートに入れていた会員Bが `/checkout` を送信する
- **影響度**: 二重販売は起きず、データも壊れない。ただし利用者は購入を完了できず、原因も分からない

表示文言だけの問題ではなく、同じ箇所に3層の課題が重なっている。

#### (a) 例外メッセージがそのまま画面に出る

`CheckoutController#submit` は `InvariantViolationException` / `IllegalStateTransitionException` を
捕捉して `e.getMessage()` をそのままフラッシュ属性に入れている。露出するのは今回の1文だけではなく、
`OrderPlacementService` が投げる `Listing が見つかりません: <UUID>` のようなメッセージも同じ経路を通る。

#### (b) カートが行き止まりになる

`CartViewAssembler#toView` はListingの状態を見ずに明細を組み立てるため、売り切れた出品もカートに
価格付きで残り「レジに進む」も表示されたままになる。エラーメッセージは商品名を含まないので、
明細が複数ある場合はどれが原因かも分からず、何度やり直しても同じ結果になる。

#### (c) 販売確定が在庫側に伝わっていない(根本原因)

`Listing#confirmSale`(USED を RESERVED → SOLD にし `ListingSold` イベントを積む)の呼び出しは
`SampleDataSeeder` にしか存在せず、実際のチェックアウト経路
(`CheckoutService` → `OrderPlacementService#placeOrder` + `PaymentCaptureService#capturePayment`)
からは一度も呼ばれていない。そのため **USED出品は購入・決済完了後も RESERVED のまま SOLD にならず、
実購入では `ListingSold` イベントが発行されない**。2人目が「RESERVED から RESERVED」に当たるのは、
1人目の購入が確定しているのに在庫側が「仮押さえ中」で止まっているため。
(a)(b) を直しても、この状態遷移の穴は残る。

#### 関連: 注文をキャンセルしても在庫が戻らない

`AdminOrderController#transitionAndRedirect` は `Order` のステータス遷移と保存しか行わず、
`Listing#cancelReservation` を呼んでいない。そのため注文をキャンセルしても在庫は復帰しない。
ST-ADM-015 はステータス遷移のみを確認するケースのため Pass しており、
仕様書にも在庫を検証する記述がないことから、テストとして見逃していた領域にあたる。

(c) と「関連」は、Ordering / Payment コンテキストから Inventory コンテキストへ
「販売確定」「キャンセル」を伝える経路が存在しない、という同じ根に行き着く。

#### 補足

修正前はこれらが500に飲み込まれていたため、前回のテストでは表面化しなかった。
また仕様書の期待文言「もう一度お試しください」も、リトライ実装後は
「競合(再試行で解決する)」と「売り切れ(再試行しても解決しない)」を区別できるようになったため、
売り切れ時には別の文言が適切。実装とあわせて仕様書側の見直しも必要。

> **対応方針(2026-08-16時点)**: 今回は記録のみとし、修正は別件として切り出す。
> (c) と「関連」はコンテキスト間連携の設計判断を伴うため。

---

## 5. 仕様書の修正候補(前回からの持ち越し)

以下は実装ではなく `system-test-spec.md` 側を直すべき項目。今回も未対応のまま。

| 箇所 | 現在の記述 | 実際 |
|---|---|---|
| 1.3 テスト環境 | ローカルのベースURLを `http://localhost:8080` としている | `docker-compose.yml` のポート公開は `8081:8080` |
| 1.4 テストデータ | 各会員に紐づく注文の状態を固定で記載 | 管理画面の操作でステータスが進むため、実施のたびに変わる |
| ST-ADM-004 | `mediaType=VINYL`、`speed=33`、`country=Japan` | 列挙子は `MediaType.LP` / `Speed.RPM_33`、`country` は2文字(`JP`) |
| ST-MEM-006 | `country=Japan` | 同上(`JP`) |
| ST-PUB-005 / ST-MEM-002 / ST-PAY-004 | ID に `999999` / `nonexistent-id` を指定 | 実装を404に揃えたため、これらの値のままでも404になる(記述はそのままで問題ない) |
| ST-PUB-006 | 前提を「在庫数0のPUBLISHED Listingが存在する」としている | 実装上そのような状態は作れない |
| ST-CONC-001 | 期待文言が「他の注文と同時に処理されたため…もう一度お試しください」 | リトライ実装後は「売り切れ」を表す文言が適切(不具合#9参照) |

### 補足: ST-PUB-006 の前提条件は依然として成立しない

1. `Listing.newCopy` は `initialStock < 1` を拒否する(今回、管理画面から `initialStock=0` を試行し
   「出品の登録に失敗しました: initialStock must be >= 1: 0」が画面表示されることを確認)
2. `Listing.reserve` は在庫0で自動的に `OUT_OF_STOCK` へ遷移する
3. `CatalogPageController#detail` は `PUBLISHED` のListingしか画面に渡さない

---

## 6. 投入したテストデータ

ST-INFRA-005(ECSタスク再起動)を最後に実施したため、以下はすべて消去済み。

| 種別 | 内容 |
|---|---|
| Release / Pressing / Listing | `Test Album` / `Test Label` `TL-001` `JP` 1990 / NEW JPY 3,500(在庫5)・USED JPY 2,800(1点限り) |
| アートワーク | 作品 `https://example.com/artwork.jpg`、プレス版 `https://example.com/pressing-artwork.jpg` |
| 注文 | 田中花子の注文1件、`POST /api/checkout` 経由3件、CONCテストで成立した3件 |
| 会員 | `cele0005+2@gmail.com`(本登録済み) |
| 仮登録 | `cele0005+3@gmail.com`(試行回数上限に到達・期限切れ) |
| 送信メール | `cele0005+2@gmail.com` 宛に確認コード2通+登録完了1通、`cele0005+3@gmail.com` 宛に確認コード1通(計4通) |
