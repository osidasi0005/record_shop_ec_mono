# phase-7: システムテスト仕様書(record-shop-ec-tests)

> このプロンプトを投げる前に、同じフォルダの `00-common.md` を読んで従うこと。
> 作業ディレクトリ: `<親フォルダ>/record-shop-ec-tests`(空フォルダを新規作成して開く)

## 1. このフェーズの目的

E2E(画面・API・権限・在庫競合・インフラ)のシステムテスト仕様書 `system-test-spec.md`(**70ケース**)と、
実施記録のテンプレート・README を作る。ドキュメントのみのリポジトリ(テストコードは含まない)。
仕様書ができたら、ローカル(Docker Compose)または AWS で実際に実施して記録を1件残す。

## 2. 前提

- phase-4 完了(ローカル実施)、phase-5 デプロイ済みならインフラ章(INFRA)も実施可能
- 実施は `curl`(cookie jar + `_csrf` トークン抽出)またはブラウザ。並行実行は `curl` をバックグラウンドで2本同時発射

## 3. 仕様

### 3.1 リポジトリ構成

```
README.md
system-test-spec.md
test-results/YYYY-MM-DD-<env>.md    (実施記録。env は local / aws)
```

### 3.2 `README.md`

- `# record-shop-ec-tests` — システムテスト仕様書と実施記録を管理するリポジトリ
- `## 対象システム` — 表: `record-shop-ec-mybatis`(バックエンドAPI + Thymeleaf画面)/ `record-shop-ec-cdk`(AWSインフラ)/ `record-shop-ec-docs`(設計ドキュメント)。GitHub の URL(`https://github.com/{GITHUB_OWNER}/...`)へリンク
- `## ドキュメント` — `system-test-spec.md` へのリンク
- `## テスト実施記録` — 表(実施日 / 環境 / 対象コミット / 結果 `Pass N / 条件付き N / Fail N / Blocked N`)。実施のたびに1行追加
- `## テスト実行環境の前提` — ローカル: `record-shop-ec-mybatis` で `docker compose up`、`http://localhost:8081`、管理者は `docker-compose.yml` の値。AWS: `npx cdk deploy RecordShopEcMybatisCdkStack` 後の `ServiceUrl`(デプロイのたびに変わる)。仕様書内は `{ベースURL}` プレースホルダ、どちらで実施したかは記録側に明記

### 3.3 `system-test-spec.md` の章立て

```
1. はじめに(1.1 目的 / 1.2 対象システム / 1.3 テスト環境 / 1.4 テストデータ / 1.5 テストID採番規則 / 1.6 テストケース表の見方)
2. テスト観点一覧(サマリ)
3. 公開機能テスト(PUB)
4. 会員機能テスト(MEM)
5. 管理者機能テスト(ADM)
6. 権限制御テスト(AUTH)
7. 並行実行・在庫競合テスト(CONC)
8. 決済フローテスト(PAY)
9. インフラ・非機能テスト(INFRA)
10. 付録(10.1 用語集 / 10.2 既知の制約)
```

- 1.1 目的: 画面/API/権限制御/在庫同時実行制御/インフラ構成を E2E で検証。ドメイン単体テスト・MyBatis 結合テストでカバーできない領域が対象
- 1.2 対象: mybatis 側は全画面(`/`, `/catalog/**`, `/login`, `/register`, `/register/confirm`, `/cart/**`, `/checkout`, `/orders/**`, `/admin/**`)と REST API(`/api/**`)、cdk 側は `RecordShopEcMybatisCdkStack`。実施の都度コミットハッシュを記録
- 1.3 環境: ローカル `http://localhost:8081` / AWS は `ServiceUrl`
- 1.4 テストデータ: サンプル顧客4名(`SampleDataSeeder`、パスワード `Passw0rd!2024`、各自の注文状態は「起動直後の状態」であり実施のたびに変わり得ると注記)、管理者(ローカル `admin@example.com` / `Passw0rd!2024`、AWS は Secrets Manager)、新規会員は `/register` から都度作成。**DB は起動のたびに初期化される**(ECS タスク再起動でも)
- 1.5 採番: `ST-<カテゴリ>-<3桁>`、カテゴリ `PUB` `MEM` `ADM` `AUTH` `CONC` `PAY` `INFRA`
- 1.6 表の列: `No | テスト項目 | 前提条件 | 手順 | 入力値 | 期待結果`
- 2章 サマリ表: PUB 19 / MEM 10 / ADM 17 / AUTH 10 / CONC 3 / PAY 4 / INFRA 7 = **70**

### 3.4 テストケース(70件)

以下を `No | テスト項目 | 前提条件 | 手順 | 入力値 | 期待結果` の表に展開する(手順は「1. …へアクセス 2. …を送信」の番号付き)。

**3章 PUB(19)**

| No | 項目 | 前提 / 入力 | 期待 |
|---|---|---|---|
| ST-PUB-001 | トップページのリダイレクト | 未ログイン、`GET /` | `/catalog` へ302 |
| ST-PUB-002 | 商品一覧: 公開済み商品の表示 | サンプルデータ | PUBLISHED Listing を持つ Release が一覧表示 |
| ST-PUB-003 | 商品一覧: 未公開商品の非表示 | ST-ADM-002〜006 で作った DRAFT のみの Release | 一覧に出ない |
| ST-PUB-004 | 商品詳細: 複数プレス版 | 複数 Pressing・公開済み Listing の Release | Pressing ごとにコンディション・価格・在庫、カート追加フォーム |
| ST-PUB-005 | 商品詳細: 存在しない releaseId | `GET /catalog/999999`(UUID形式でない)および形式は正しい未存在UUID | いずれも 404(HTML) |
| ST-PUB-006 | 商品詳細: 在庫切れ Pressing の表示 | NEW 在庫1の Listing を別会員が購入済み(OUT_OF_STOCK)。同 Pressing に他の公開 Listing なし | その Pressing は「現在購入可能な出品はありません。」と表示され、カート追加フォームが出ない |
| ST-PUB-007 | ログイン成功 | `tanaka.hanako@example.com` / `Passw0rd!2024` | `/catalog` へ、ナビに会員向けリンク |
| ST-PUB-008 | ログイン失敗: パスワード誤り | password=`WrongPassword` | ログイン画面に留まりエラー |
| ST-PUB-009 | ログイン失敗: 未登録メール | `notexist@example.com` | 同上 |
| ST-PUB-010 | 会員登録: 正常系 | email=`newuser001@example.com` / `Passw0rd!2024` / `新規太郎`(AWS では SES 検証済み宛先を使う) | `/register/confirm?email=...` へ、6桁コードがメール送信される(ローカルで SES 未設定なら送信失敗メッセージが出て仮登録は残らない、と注記) |
| ST-PUB-011 | パスワード8文字未満 | `Pass1!` | 「パスワードは8文字以上で入力してください」、仮登録なし |
| ST-PUB-012 | メール形式不正 | `invalid-email` | 「メールアドレスの形式が正しくありません」 |
| ST-PUB-013 | 表示名未入力 | displayName 空 | 「表示名を入力してください」 |
| ST-PUB-014 | 登録済みメールで重複 | `tanaka.hanako@example.com` | 既に登録されている旨のエラー |
| ST-PUB-015 | 同一メールでの再登録要求 | ST-PUB-010 の仮登録が未確認 | 確認コード画面へ、コード再発行・試行回数リセット、旧コード無効 |
| ST-PUB-016 | 確認コード: 正しいコード | 有効なコード | `/login?registered` へ、Customer 本登録、仮登録削除 |
| ST-PUB-017 | 確認コード: 誤ったコード | `000000` | 画面に留まり「確認コードが正しくありません」、試行回数+1 |
| ST-PUB-018 | 確認コード: 有効期限切れ | 発行から10分経過 | 「確認コードの有効期限が切れています。もう一度会員登録をやり直してください」 |
| ST-PUB-019 | 確認コード: 試行上限(5回)超過 | 誤5回→正1回 | 6回目も「確認コードの入力回数が上限に達しました。…」 |

**4章 MEM(10)**(注記: Cart はセッション保持のみ)

| No | 項目 | 前提 / 入力 | 期待 |
|---|---|---|---|
| ST-MEM-001 | カート追加: 在庫あり | 田中でログイン、PUBLISHED Listing、quantity=1 | `/cart` へ、1件追加 |
| ST-MEM-002 | カート追加: 存在しない listingId | `POST /cart/add listingId=nonexistent-id` | `/catalog` へ、「指定された商品が見つかりませんでした」 |
| ST-MEM-003 | カート削除 | カートに1件 | `/cart` へ、消える |
| ST-MEM-004 | カート表示: 複数商品・合計 | 2商品 | 明細2行、合計=価格×数量の和 |
| ST-MEM-005 | 未ログインでのカート | 未ログイン `GET /cart` | `/login` へ302 |
| ST-MEM-006 | チェックアウト正常系 | recipientName=`田中花子` postalCode=`100-0001` prefecture=`東京都` city=`千代田区` addressLine=`1-1-1` **country=`JP`** | 在庫予約→Order→即時Capture、`/orders/{orderId}` へ、カート空 |
| ST-MEM-007 | チェックアウト: カート空 | `GET /checkout` | `/cart` へ、「カートが空です」 |
| ST-MEM-008 | 注文履歴: 自分の分のみ | 佐藤でログイン | 佐藤の注文のみ新しい順 |
| ST-MEM-009 | 注文詳細: 自分の注文 | | 注文番号・日時・ステータス・明細(スナップショット)・お支払い状況 |
| ST-MEM-010 | 注文詳細: 他人の注文ID | 田中で佐藤の注文ID | 404 |

**5章 ADM(17)**(以降の前提データ作成も兼ねる)

| No | 項目 | 前提 / 入力 | 期待 |
|---|---|---|---|
| ST-ADM-001 | 作品一覧 | 管理者 | 全 Release と Pressing 数 |
| ST-ADM-002 | 作品新規登録 | title=`Test Album` artistName=`Test Artist` genres=`Jazz,Fusion` originalReleaseYear=`1990` | `/admin/releases/{id}` へ |
| ST-ADM-003 | 作品登録: タイトル未入力 | title 空 | 登録画面に留まりエラー |
| ST-ADM-004 | プレス版追加: 正常系 | labelName=`Test Label` catalogNumber=`TL-001` **country=`JP`** pressYear=`1990` reissue=false **mediaType=`LP` speed=`RPM_33`** discCount=`1` | 詳細へ、追加表示 |
| ST-ADM-005 | プレス版追加: 不正入力 | country=`Japan`(3文字以上)/ pressYear=`1700` | 詳細に留まり「国コードはISO 3166-1 alpha-2形式…」等のエラー。(型変換エラー `pressYear=abc` は400になることを注記) |
| ST-ADM-006 | Listing 作成: NEW | conditionType=`NEW` priceAmount=`3500` priceCurrency=`JPY` initialStock=`5` | DRAFT で追加、`/catalog` 未反映 |
| ST-ADM-007 | Listing 作成: USED | `USED` `2800` vinylGrade=`VERY_GOOD_PLUS` sleeveGrade=`VERY_GOOD` sellerNote=`盤面良好` | DRAFT、在庫1 |
| ST-ADM-008 | Listing 公開 | DRAFT | PUBLISHED、`/catalog` に反映 |
| ST-ADM-009 | アートワークURL: 作品 | `https://example.com/artwork.jpg` | 「アートワークを更新しました」、一覧・詳細に画像 |
| ST-ADM-010 | アートワークURL: プレス版 | `https://example.com/pressing-artwork.jpg` | 該当 Pressing のみ反映 |
| ST-ADM-011 | 注文一覧: 全顧客 | 複数会員の注文 | 全件、新しい順 |
| ST-ADM-012 | 入金確認 PENDING→PAID | 田中の注文(USED) | 「入金を確認しました」。**該当 USED Listing が SOLD になる** |
| ST-ADM-013 | 発送済み PAID→SHIPPED | | 「発送済みにしました」 |
| ST-ADM-014 | 配達完了 SHIPPED→DELIVERED | 鈴木の注文 | 「配達完了にしました」 |
| ST-ADM-015 | 注文キャンセル | PENDING/PAID | CANCELLED、「注文をキャンセルしました」 |
| ST-ADM-016 | 不正な遷移操作 | DELIVERED に `mark-paid` | 詳細に留まりエラー、状態不変 |
| ST-ADM-017 | **キャンセルで在庫が戻る** | NEW 在庫N の Listing を含む注文を作り(在庫 N-1)、管理画面でキャンセル | Listing の在庫が N に戻る。USED を含む場合は RESERVED→PUBLISHED に戻り `/catalog` に再表示 |

**6章 AUTH(10)**: 匿名×`/catalog`=200 / 匿名×`/cart`=302 `/login` / 匿名×`/admin/releases`=302 `/login` / CUSTOMER×`/catalog`=200 / CUSTOMER×`/orders`=200 / CUSTOMER×`/admin/releases`=403 / ADMIN×`/catalog`=200 / ADMIN×`/cart`=200 / ADMIN×`/admin/orders`=200 / 匿名×`GET /api/releases/{id}`=200(permitAll が意図的設計であることの確認)

**7章 CONC(3)**(`listings.version` の楽観ロックと `CheckoutService` のリトライを検証。`MyBatisListingOptimisticLockingTest` のシステムレベル版。`curl` 並行スクリプト推奨)

| No | 項目 | 前提 / 入力 | 期待 |
|---|---|---|---|
| ST-CONC-001 | 同時チェックアウトによる在庫競合(在庫1) | 在庫1の USED(PUBLISHED)、会員A(田中)・B(佐藤)が同じ Listing をカートに追加済み、ほぼ同時に `POST /checkout` | 片方は `/orders/{id}` へ。もう片方は `/cart` へ戻り **「売り切れのため確定できませんでした: {作品名}」**(リトライで解決しない売り切れとして扱う)。Order は1件のみ、二重販売なし。カートから当該商品が消えている |
| ST-CONC-002 | 在庫十分な同時アクセス(対照) | 在庫2以上の NEW、A・B 各1点 | 両方成功(楽観ロック競合が起きてもリトライで吸収)、在庫が2減り Order 2件 |
| ST-CONC-003 | **売り切れ品を含むカートのチェックアウト(同時実行なし)** | USED 在庫1を A が購入して SOLD。同じ Listing を B がカートに入れていた | B の `/cart` 表示時に当該行が消え「『{作品名}』は売り切れのためカートから削除しました」。残った商品だけでチェックアウトできる |

**8章 PAY(4)**(Capture は常に即成功の擬似実装。成功系のみ)

| No | 項目 | 前提 / 入力 | 期待 |
|---|---|---|---|
| ST-PAY-001 | チェックアウト連動の即時 Capture | ST-MEM-006 | `/orders/{id}` に「お支払い状況: CAPTURED」 |
| ST-PAY-002 | REST 経由の Capture | PENDING Order、`POST /api/orders/{id}/payments {"method":"CREDIT_CARD"}` | 201、status=CAPTURED、Order PAID、USED Listing SOLD |
| ST-PAY-003 | 決済情報の取得 | `GET /api/payments/{id}` | 200 |
| ST-PAY-004 | 存在しない PaymentId | `nonexistent-id` | 404 |

**9章 INFRA(7)**(AWS デプロイ済み前提)

| No | 項目 | 期待 |
|---|---|---|
| ST-INFRA-001 | `http://{CloudFrontドメイン}/catalog` | HTTPS へリダイレクト |
| ST-INFRA-002 | `ServiceUrl` へアクセス | 200 |
| ST-INFRA-003 | `AlbDirectUrl` へアクセス | 200(オリジン疎通) |
| ST-INFRA-004 | `{ALB}/actuator/health` | 200 |
| ST-INFRA-005 | カートの非永続性 | `aws ecs update-service --cluster <cluster> --service <service> --force-new-deployment` 後、`/cart` が空(DBもサンプル初期状態に戻る) |
| ST-INFRA-006 | `cdk deploy` 後の URL 取得 | Outputs に `ServiceUrl` / `AlbDirectUrl` |
| ST-INFRA-007 | 会員登録確認メールの SES 送信 | SES 検証済み宛先に6桁コードが届く |

**10章 付録**: 用語集9語(Release / Pressing / Listing / Cart / Order / Payment / Customer / CUSTOMER / ADMIN、状態遷移も明記)、既知の制約(決済は擬似実装 / Cart 非永続 / AWS は一時環境でドメインが変わる / DB は起動のたびに初期化 / コンテキスト間は FK 無し ID 参照)

### 3.5 実施記録テンプレート `test-results/YYYY-MM-DD-<env>.md`

```
# システムテスト実施記録(YYYY-MM-DD、<環境>)
## 1. 実施サマリ
| 項目 | 内容 |  ← 実施日(時間帯) / 環境 / {ベースURL} / AlbDirectUrl(AWS時) / 対象コミット(mybatis, cdk, tests) / 実施方法
### 結果内訳   Pass / Pass(条件付き) / Fail / Blocked / 合計(判定の定義を表で)
### カテゴリ別内訳   PUB/MEM/ADM/AUTH/CONC/PAY/INFRA ごとに Pass/条件付き/Fail/Blocked
## 2〜8. 各章の結果(No / 判定 / 備考)
## 9. 検出した不具合(#N: 対象ケース / 期待 / 実際 / 原因 / ログ / 影響)
## 10. 仕様書と実装の差異(仕様書側の修正候補)
## 11. 投入したテストデータ(後片付けの要否)
```

### 3.6 実施

- 仕様書完成後、少なくともローカル(Docker Compose)で PUB/MEM/ADM/AUTH/CONC/PAY を実施し `test-results/YYYY-MM-DD-local.md` を作る。AWS デプロイ済みなら INFRA も
- Fail が出たら mybatis 側を修正し、再実施記録を追加(README の表も更新)

### 3.7 GitHub 公開

```bash
gh repo create {GITHUB_OWNER}/record-shop-ec-tests --private --source=. --remote=origin --push
```

## 4. 注意

- 入力値は**実装の Enum・検証に合わせる**(`LP`/`RPM_33`、国コード `JP`)。元プロジェクトでは仕様書に `VINYL`/`33`/`Japan` と書いて「Pass(条件付き)」を量産した
- UUID 形式でない ID(`999999`、`nonexistent-id`)は 404 になる実装(phase-3 `PathIds`)。仕様書の期待もそれに合わせる
- ST-PUB-006 は「在庫0の PUBLISHED Listing」を前提にすると成立しない(`newCopy` が在庫0を拒否、在庫0で自動 OUT_OF_STOCK)。上記の前提で書く
- 実施記録に実メールアドレスを残さない

## 5. 完了条件

- `system-test-spec.md` に 70 ケースが表で揃い、2章のサマリと一致
- `test-results/` に実施記録が1件以上、README の表に反映
- push 完了

## 6. コミット

- `初回コミット: README・システムテスト仕様書(第1〜2章)`
- `第3〜10章を追加(全70ケース)`
- `YYYY-MM-DDのシステムテスト実施記録を追加(<環境>)`
