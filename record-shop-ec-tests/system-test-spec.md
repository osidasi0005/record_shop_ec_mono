# システムテスト仕様書

レコード販売ECサイト([record-shop-ec-mybatis](https://github.com/osidasi0005/record-shop-ec-mybatis) +
[record-shop-ec-cdk](https://github.com/osidasi0005/record-shop-ec-cdk))を対象としたシステムテスト仕様書。

## 目次

1. [はじめに](#1-はじめに)
2. [テスト観点一覧(サマリ)](#2-テスト観点一覧サマリ)
3. [公開機能テスト](#3-公開機能テスト)
4. [会員機能テスト](#4-会員機能テスト)
5. [管理者機能テスト](#5-管理者機能テスト)
6. [権限制御テスト](#6-権限制御テスト)
7. [並行実行・在庫競合テスト](#7-並行実行在庫競合テスト)
8. [決済フローテスト](#8-決済フローテスト)
9. [インフラ・非機能テスト](#9-インフラ非機能テスト)
10. [付録](#10-付録)

---

## 1. はじめに

### 1.1 目的

`record-shop-ec-mybatis`(バックエンドAPI + Thymeleaf画面)を中心に、画面・API・権限制御・在庫の
同時実行制御・インフラ構成(`record-shop-ec-cdk`)までを対象に、実際にブラウザ・APIクライアントを
操作してエンドツーエンドで動作を検証するためのテストケース集。ドメイン層の単体テストや
MyBatisリポジトリの結合テストではカバーできない、画面遷移・HTTPステータス・複数コンポーネント
連携・実行環境固有の挙動を確認することを目的とする。

### 1.2 対象システム

| リポジトリ | 対象範囲 |
|---|---|
| [record-shop-ec-mybatis](https://github.com/osidasi0005/record-shop-ec-mybatis) | 全画面(`/`, `/catalog/**`, `/login`, `/register`, `/cart/**`, `/checkout`, `/orders/**`, `/admin/**`)、REST API(`/api/**`) |
| [record-shop-ec-cdk](https://github.com/osidasi0005/record-shop-ec-cdk) | `RecordShopEcMybatisCdkStack`(VPC / RDS / ECS Fargate / ALB / CloudFront) |

対象バージョン: 各リポジトリの `main` ブランチ最新(実施のつどコミットハッシュを記録すること)。

### 1.3 テスト環境

以下いずれかの環境で実施する。テスト実施記録には、どちらの環境で実施したかを必ず明記する。

| 環境 | ベースURL(`{ベースURL}`) | 起動方法 |
|---|---|---|
| ローカル(Docker Compose) | `http://localhost:8080` | `record-shop-ec-mybatis` で `docker compose up` |
| AWS(CloudFront経由) | デプロイのたびに変わる(`cdk deploy` 出力の `ServiceUrl` を参照) | `record-shop-ec-cdk` で `npx cdk deploy RecordShopEcMybatisCdkStack` |

> AWS環境は学習・デモ用途の一時環境であり、コスト最小化のため未使用時は `cdk destroy` される運用。
> システムテストをAWS環境で実施する場合は、事前にデプロイされていることを確認すること。

### 1.4 テストデータ

#### サンプル顧客アカウント(`SampleDataSeeder`により起動時自動投入、全員ロール: CUSTOMER)

| 表示名 | メールアドレス(ログインID) | パスワード | 紐づく注文の状態 |
|---|---|---|---|
| 田中 花子 | `tanaka.hanako@example.com` | `Passw0rd!2024` | Thriller(Used)、Order: PENDING / Payment: PENDING(銀行振込未払い) |
| 佐藤 次郎 | `sato.jiro@example.com` | `Passw0rd!2024` | Selected Ambient Works 85-92(Used)、Order: PAID / Payment: CAPTURED |
| 鈴木 美咲 | `suzuki.misaki@example.com` | `Passw0rd!2024` | 複数明細(NEW×2 + USED×1)、Order: SHIPPED / Payment: CAPTURED |
| 高橋 健太 | `takahashi.kenta@example.com` | `Passw0rd!2024` | Nevermind(NEW)、Order: CANCELLED / Payment: REFUNDED |

#### 管理者アカウント(`AdminAccountSeeder`により起動時自動作成、自己登録不可)

| 環境 | メールアドレス | パスワード |
|---|---|---|
| ローカル(Docker Compose) | `admin@example.com` | `Passw0rd!2024`(`docker-compose.yml` に平文設定) |
| AWS | `admin@example.com` | AWS Secrets Managerで自動生成(シークレット名 `AdminPassword...`。`aws secretsmanager get-secret-value` で取得) |

新規会員アカウントが必要なテストケースでは `/register` から都度作成する(常にCUSTOMERロールで作成される)。

### 1.5 テストID採番規則

`ST-<カテゴリ>-<3桁連番>` の形式とする。

| カテゴリコード | 対応する章 |
|---|---|
| `PUB` | 3. 公開機能テスト |
| `MEM` | 4. 会員機能テスト |
| `ADM` | 5. 管理者機能テスト |
| `AUTH` | 6. 権限制御テスト |
| `CONC` | 7. 並行実行・在庫競合テスト |
| `PAY` | 8. 決済フローテスト |
| `INFRA` | 9. インフラ・非機能テスト |

例: `ST-MEM-003` = 会員機能テストの3番目のケース。

### 1.6 テストケース表の見方

各章のテストケースは以下の列で構成する共通フォーマットの表で記述する。

| No | テスト項目 | 前提条件 | 手順 | 入力値 | 期待結果 |
|---|---|---|---|---|---|

- **前提条件**: テスト実施前に満たしておくべき状態(ログイン状態、データの状態など)
- **手順**: 実施者が実際に行う操作を番号付きで記載
- **入力値**: フォーム入力やAPIリクエストボディの具体的な値
- **期待結果**: 画面表示・HTTPステータス・DB状態など、確認すべき結果

---

## 2. テスト観点一覧(サマリ)

| カテゴリコード | 章 | 対象画面・API | ケース数 |
|---|---|---|---|
| `PUB` | [3. 公開機能テスト](#3-公開機能テスト) | `/`, `/catalog`, `/catalog/{releaseId}`, `/login`, `/register`, `/register/confirm` | 19 |
| `MEM` | [4. 会員機能テスト](#4-会員機能テスト) | `/cart`, `/cart/add`, `/cart/remove`, `/checkout`, `/orders`, `/orders/{orderId}` | 10 |
| `ADM` | [5. 管理者機能テスト](#5-管理者機能テスト) | `/admin/releases/**`, `/admin/listings/**`, `/admin/orders/**` | 16 |
| `AUTH` | [6. 権限制御テスト](#6-権限制御テスト) | 上記全画面区分 × ロール(匿名/CUSTOMER/ADMIN) | 10 |
| `CONC` | [7. 並行実行・在庫競合テスト](#7-並行実行在庫競合テスト) | `/checkout`(同時アクセス時の楽観ロック) | 2 |
| `PAY` | [8. 決済フローテスト](#8-決済フローテスト) | `/api/orders/{orderId}/payments`, `/api/payments/{paymentId}`, チェックアウト連動 | 4 |
| `INFRA` | [9. インフラ・非機能テスト](#9-インフラ非機能テスト) | CloudFront/ALB/カートの非永続性/CDKデプロイ運用/CI-CDパイプライン | 12 |
| **合計** | | | **73** |

---

## 3. 公開機能テスト

認証不要でアクセスできる画面(`/`, `/catalog/**`, `/login`, `/register`, `/register/confirm`)を対象とする。

| No | テスト項目 | 前提条件 | 手順 | 入力値 | 期待結果 |
|---|---|---|---|---|---|
| ST-PUB-001 | トップページのリダイレクト | 未ログイン状態 | 1. `{ベースURL}/` へアクセスする | - | `/catalog` へ302リダイレクトされ、商品一覧が表示される |
| ST-PUB-002 | 商品一覧: 公開済み商品の表示 | PUBLISHED状態のListingを持つReleaseが1件以上存在する(サンプルデータで充足) | 1. `{ベースURL}/catalog` へアクセスする | - | PUBLISHED状態のListingを持つReleaseが一覧表示される |
| ST-PUB-003 | 商品一覧: 未公開商品の非表示 | ST-ADM-002(作品登録)〜ST-ADM-006(Listing作成、未publish)で作成したReleaseが存在する(publishしていないためDRAFT状態) | 1. `{ベースURL}/catalog` へアクセスする | - | DRAFT状態のListingしか持たないReleaseは一覧に表示されない |
| ST-PUB-004 | 商品詳細: 正常表示(複数プレス版) | 複数のPressing・公開済みListingを持つReleaseが存在する | 1. `{ベースURL}/catalog` から対象Releaseの詳細リンクをクリックする | - | `/catalog/{releaseId}` に遷移し、Pressingごとにコンディション・価格・在庫数が表示される。「カートに追加」フォームが表示される |
| ST-PUB-005 | 商品詳細: 存在しないreleaseId | - | 1. `{ベースURL}/catalog/999999`(存在しないID)へ直接アクセスする | releaseId=999999 | 404 Not Found が返る |
| ST-PUB-006 | 商品詳細: 在庫0のPressing表示 | 在庫数0のPUBLISHED Listingを持つPressingが存在する | 1. 対象Releaseの詳細画面を開く | - | 在庫0であることが表示され、「カートに追加」操作が抑止される(ボタン非活性、または追加不可の旨のメッセージ) |
| ST-PUB-007 | ログイン成功 | サンプル顧客アカウント(田中花子)が存在する | 1. `{ベースURL}/login` へアクセス<br>2. フォームに入力して送信する | email=`tanaka.hanako@example.com`<br>password=`Passw0rd!2024` | `/catalog` へリダイレクトされ、ログイン済み状態(ナビゲーションに会員向けリンクが表示される)になる |
| ST-PUB-008 | ログイン失敗: パスワード誤り | 同上 | 1. `{ベースURL}/login` へアクセス<br>2. フォームに入力して送信する | email=`tanaka.hanako@example.com`<br>password=`WrongPassword` | ログインページに留まり、認証エラーメッセージが表示される。ログイン状態にならない |
| ST-PUB-009 | ログイン失敗: 未登録メール | 未ログイン状態 | 1. `{ベースURL}/login` へアクセス<br>2. フォームに入力して送信する | email=`notexist@example.com`<br>password=`Passw0rd!2024` | ログインページに留まり、認証エラーメッセージが表示される |
| ST-PUB-010 | 会員登録: 正常系 | 未使用のメールアドレスを用意する | 1. `{ベースURL}/register` へアクセス<br>2. フォームに入力して送信する | email=`newuser001@example.com`<br>password=`Passw0rd!2024`<br>displayName=`新規太郎` | `/register/confirm?email=newuser001@example.com` へリダイレクトされ、確認コード入力画面が表示される。登録用メールアドレス宛に6桁の確認コードが送信される(ローカル環境ではログ等でコードを確認する運用を想定) |
| ST-PUB-011 | 会員登録: パスワード8文字未満 | 未使用のメールアドレスを用意する | 1. `{ベースURL}/register` へアクセス<br>2. フォームに入力して送信する | email=`newuser002@example.com`<br>password=`Pass1!`(6文字)<br>displayName=`テスト太郎` | 登録画面に留まり、「パスワードは8文字以上で入力してください」が表示される。仮登録は作成されない |
| ST-PUB-012 | 会員登録: メール形式不正 | - | 1. `{ベースURL}/register` へアクセス<br>2. フォームに入力して送信する | email=`invalid-email`<br>password=`Passw0rd!2024`<br>displayName=`テスト太郎` | 登録画面に留まり、「メールアドレスの形式が正しくありません」が表示される |
| ST-PUB-013 | 会員登録: 表示名未入力 | - | 1. `{ベースURL}/register` へアクセス<br>2. displayNameを空欄のまま送信する | email=`newuser003@example.com`<br>password=`Passw0rd!2024`<br>displayName=(空欄) | 登録画面に留まり、「表示名を入力してください」が表示される |
| ST-PUB-014 | 会員登録: 登録済みメールで重複エラー | サンプル顧客(田中花子)が登録済み | 1. `{ベースURL}/register` へアクセス<br>2. フォームに入力して送信する | email=`tanaka.hanako@example.com`<br>password=`Passw0rd!2024`<br>displayName=`偽花子` | 登録画面に留まり、メールアドレスが既に使用されている旨のエラーが表示される(`CustomerRegistrationService.assertEmailAvailable`による拒否) |
| ST-PUB-015 | 会員登録: 同一メールでの再登録要求(コード再発行) | ST-PUB-010で仮登録済み(`newuser001@example.com`、未確認) | 1. 同じメールアドレスで再度`/register`を送信する | email=`newuser001@example.com`<br>password=`Passw0rd!2024`<br>displayName=`新規太郎` | 確認コード画面へ再度リダイレクトされる。確認コードが新しく発行され、試行回数がリセットされる(旧コードは無効化される) |
| ST-PUB-016 | 確認コード: 正しいコードで登録完了 | ST-PUB-010で仮登録済み。有効な確認コードを把握している | 1. `/register/confirm?email=newuser001@example.com` にアクセス<br>2. 確認コードを入力して送信する | code=(発行された6桁コード) | `/login?registered` へリダイレクトされる。Customerが本登録され、以後そのメール・パスワードでログイン可能になる。仮登録レコードは削除される |
| ST-PUB-017 | 確認コード: 誤ったコード | 未確認の仮登録が存在する | 1. `/register/confirm?email=...` にアクセス<br>2. 誤ったコードを入力して送信する | code=`000000`(発行コードと異なる値) | 確認コード画面に留まり、「確認コードが正しくありません」が表示される。試行回数が1増える |
| ST-PUB-018 | 確認コード: 有効期限切れ(10分経過) | 未確認の仮登録が存在し、発行から10分以上経過している | 1. 10分経過後に`/register/confirm?email=...`へアクセス<br>2. 正しいコードを入力して送信する | code=(発行された6桁コード) | 「確認コードの有効期限が切れています。もう一度会員登録をやり直してください」が表示され、登録は完了しない |
| ST-PUB-019 | 確認コード: 試行回数上限(5回)超過 | 未確認の仮登録が存在する | 1. 誤ったコードを5回連続で送信する<br>2. 6回目に正しいコードを送信する | 1〜5回目: 誤ったコード<br>6回目: 正しいコード | 6回目も「確認コードの入力回数が上限に達しました。もう一度会員登録をやり直してください」が表示され、正しいコードでも登録できない |

---

## 4. 会員機能テスト

要ログインの画面(`/cart`, `/checkout`, `/orders/**`)を対象とする。Cart集約はセッション保持のみでDBに永続化されない点に注意。

| No | テスト項目 | 前提条件 | 手順 | 入力値 | 期待結果 |
|---|---|---|---|---|---|
| ST-MEM-001 | カート追加: 在庫あり | 田中花子でログイン済み。PUBLISHED状態・在庫ありのListingが存在する | 1. 商品詳細画面で数量を指定し「カートに追加」を送信する | listingId=(対象Listing)<br>quantity=1 | `/cart` へリダイレクトされ、対象商品がカートに1件追加される |
| ST-MEM-002 | カート追加: 存在しないlistingId | ログイン済み | 1. `POST /cart/add` に存在しないIDを直接送信する | listingId=`nonexistent-id` | `/catalog` へリダイレクトされ、「指定された商品が見つかりませんでした」のフラッシュメッセージが表示される。カートには追加されない |
| ST-MEM-003 | カート削除 | カートに1件以上商品が入っている | 1. カート画面で対象商品の「削除」を送信する | listingId=(カート内の対象) | `/cart` へリダイレクトされ、対象商品がカートから消える |
| ST-MEM-004 | カート表示: 複数商品・合計金額 | 異なる2商品をカートに追加済み | 1. `{ベースURL}/cart` へアクセスする | - | 2商品が明細として表示され、合計金額が各明細の価格×数量の合計と一致する |
| ST-MEM-005 | 未ログインでのカートアクセス | 未ログイン状態 | 1. `{ベースURL}/cart` へアクセスする | - | `/login` へ302リダイレクトされる |
| ST-MEM-006 | チェックアウト正常系 | ログイン済み、カートに在庫ありの商品が1件以上入っている | 1. `{ベースURL}/checkout` へアクセスしフォームに入力して送信する | recipientName=`田中花子`<br>postalCode=`100-0001`<br>prefecture=`東京都`<br>city=`千代田区`<br>addressLine=`1-1-1`<br>country=`Japan` | 在庫予約→Order作成→即時決済Captureが行われ、`/orders/{orderId}` へリダイレクトされる。セッションのカートは空になる |
| ST-MEM-007 | チェックアウト: カートが空 | ログイン済み、カートが空の状態 | 1. `{ベースURL}/checkout` へアクセスする | - | `/cart` へリダイレクトされ、「カートが空です」のフラッシュメッセージが表示される |
| ST-MEM-008 | 注文履歴一覧: 自分の注文のみ表示 | 佐藤次郎でログイン済み(佐藤自身の注文が1件以上あり、他会員の注文も存在する) | 1. `{ベースURL}/orders` へアクセスする | - | 佐藤次郎自身の注文のみが新しい順に一覧表示され、他会員(田中花子等)の注文は表示されない |
| ST-MEM-009 | 注文詳細: 自分の注文 | ログイン中の会員自身の注文が存在する | 1. `/orders` から対象注文の詳細リンクをクリックする | - | `/orders/{orderId}` に遷移し、注文番号・注文日時・ステータス・明細(購入時点のPressing情報スナップショット)が表示される |
| ST-MEM-010 | 注文詳細: 他人の注文IDを直接指定 | 田中花子でログイン済み。佐藤次郎の注文IDを把握している | 1. `{ベースURL}/orders/{佐藤次郎の注文ID}` に直接アクセスする | orderId=(佐藤次郎の注文ID) | 404 Not Found が返る(所有者チェックにより中身は見せない) |

---

## 5. 管理者機能テスト

要ROLE_ADMINの画面(`/admin/**`)を対象とする。以降のテストの前提データとしても利用する。

| No | テスト項目 | 前提条件 | 手順 | 入力値 | 期待結果 |
|---|---|---|---|---|---|
| ST-ADM-001 | 作品一覧表示 | 管理者アカウントでログイン済み | 1. `{ベースURL}/admin/releases` へアクセスする | - | 登録済み全Releaseと、紐づくPressing数が一覧表示される |
| ST-ADM-002 | 作品新規登録: 正常系 | 管理者アカウントでログイン済み | 1. `{ベースURL}/admin/releases/new` へアクセスしフォームに入力して送信する | title=`Test Album`<br>artistName=`Test Artist`<br>genres=`Jazz,Fusion`<br>originalReleaseYear=`1990`<br>artworkUrl=(空欄) | `/admin/releases/{releaseId}` へリダイレクトされ、作品詳細画面に登録内容が反映されている |
| ST-ADM-003 | 作品新規登録: タイトル未入力 | 管理者アカウントでログイン済み | 1. `/admin/releases/new` でtitleを空欄のまま送信する | title=(空欄)<br>artistName=`Test Artist` | 登録画面に留まり、「タイトルを入力してください」が表示される。作品は登録されない |
| ST-ADM-004 | プレス版追加: 正常系 | ST-ADM-002で作品を登録済み | 1. 作品詳細画面の「プレス版追加」フォームに入力して送信する | labelName=`Test Label`<br>catalogNumber=`TL-001`<br>country=`Japan`<br>pressYear=`1990`<br>matrixRunout=(空欄)<br>reissue=`false`<br>mediaType=`VINYL`<br>speed=`33`<br>discCount=`1`<br>artworkUrl=(空欄) | `/admin/releases/{releaseId}` へリダイレクトされ、追加したプレス版が表示される |
| ST-ADM-005 | プレス版追加: 不正な入力 | 同上 | 1. 必須項目を欠いた状態、または不正な値でプレス版追加フォームを送信する | pressYear=(不正な文字列など) | 作品詳細画面に留まり、エラーメッセージ(`InvariantViolationException`/`IllegalArgumentException`起因)が表示される。プレス版は追加されない |
| ST-ADM-006 | Listing作成: NEW | ST-ADM-004でプレス版を追加済み | 1. 作品詳細画面のListing作成フォームに入力して送信する | pressingId=(対象)<br>conditionType=`NEW`<br>priceAmount=`3500`<br>priceCurrency=`JPY`<br>initialStock=`5` | 作品詳細画面へリダイレクトされ、DRAFT状態のListingが一覧に追加される(この時点では`/catalog`には表示されない) |
| ST-ADM-007 | Listing作成: USED | 同上のプレス版が存在する | 1. Listing作成フォームでUSED条件を入力して送信する | pressingId=(対象)<br>conditionType=`USED`<br>priceAmount=`2800`<br>priceCurrency=`JPY`<br>vinylGrade=`VERY_GOOD_PLUS`<br>sleeveGrade=`VERY_GOOD`<br>sellerNote=`盤面良好` | 作品詳細画面へリダイレクトされ、DRAFT状態のUsed Listing(在庫数1)が追加される |
| ST-ADM-008 | Listing公開 | ST-ADM-006またはST-ADM-007でDRAFT状態のListingを作成済み | 1. 作品詳細画面で対象Listingの「公開」ボタンを送信する | listingId=(対象) | Listingのステータスが DRAFT→PUBLISHED に遷移し、`/catalog` の商品一覧・詳細に反映される |
| ST-ADM-009 | ジャケット画像URL設定: 作品全体 | ST-ADM-002で作品を登録済み | 1. 作品詳細画面のアートワークフォームにURLを入力して送信する | artworkUrl=`https://example.com/artwork.jpg` | `/admin/releases/{releaseId}` へリダイレクトされ、「アートワークを更新しました」が表示される。`/catalog`・商品詳細画面にサムネイル/大きい画像が表示される |
| ST-ADM-010 | ジャケット画像URL設定: プレス版個別 | ST-ADM-004でプレス版を追加済み | 1. プレス版ごとのアートワークフォームにURLを入力して送信する | pressingId=(対象)<br>artworkUrl=`https://example.com/pressing-artwork.jpg` | 「アートワークを更新しました」が表示され、該当プレス版のみ個別の画像が商品詳細画面に反映される |
| ST-ADM-011 | 注文一覧表示: 全顧客対象 | 複数会員の注文が存在する | 1. `{ベースURL}/admin/orders` へアクセスする | - | 全顧客の注文が新しい順に一覧表示される(会員向け`/orders`と異なりログイン中会員に限定されない) |
| ST-ADM-012 | 注文ステータス遷移: 入金確認 | PENDING状態の注文が存在する(田中花子の注文など) | 1. `/admin/orders/{orderId}` で「入金を確認する」を送信する | orderId=(PENDING状態の注文) | PENDING→PAID に遷移し、「入金を確認しました」が表示される |
| ST-ADM-013 | 注文ステータス遷移: 発送済み | PAID状態の注文が存在する | 1. 「発送済みにする」を送信する | orderId=(PAID状態の注文) | PAID→SHIPPED に遷移し、「発送済みにしました」が表示される |
| ST-ADM-014 | 注文ステータス遷移: 配達完了 | SHIPPED状態の注文が存在する(鈴木美咲の注文など) | 1. 「配達完了にする」を送信する | orderId=(SHIPPED状態の注文) | SHIPPED→DELIVERED に遷移し、「配達完了にしました」が表示される |
| ST-ADM-015 | 注文キャンセル | PENDINGまたはPAID状態の注文が存在する | 1. 「注文をキャンセル」を送信する | orderId=(PENDINGまたはPAID状態の注文) | 対象状態→CANCELLED に遷移し、「注文をキャンセルしました」が表示される |
| ST-ADM-016 | 不正なステータス遷移操作 | DELIVERED状態の注文が存在する(高橋健太の注文はCANCELLEDのため、別途DELIVEREDにした注文を使う) | 1. `POST /admin/orders/{orderId}/mark-paid` をDELIVERED状態の注文に対して直接送信する | orderId=(DELIVERED状態の注文) | 詳細画面に留まり、`IllegalStateTransitionException`起因のエラーメッセージが表示される。ステータスはDELIVEREDのまま変化しない |

---

## 6. 権限制御テスト

`record-shop-ec-docs` リポジトリの `05-screen-spec.md`(権限マトリクス)と `SecurityConfig` を基に、
匿名・CUSTOMER・ADMINの3ロール×画面区分の組み合わせを網羅する。

| No | テスト項目 | 前提条件 | 手順 | 入力値 | 期待結果 |
|---|---|---|---|---|---|
| ST-AUTH-001 | 匿名: 公開画面へのアクセス | 未ログイン状態 | 1. `{ベースURL}/catalog` へアクセスする | - | 200で商品一覧が表示される |
| ST-AUTH-002 | 匿名: 会員向け画面へのアクセス | 未ログイン状態 | 1. `{ベースURL}/cart` へアクセスする | - | `/login` へ302リダイレクトされる |
| ST-AUTH-003 | 匿名: 管理者向け画面へのアクセス | 未ログイン状態 | 1. `{ベースURL}/admin/releases` へアクセスする | - | `/login` へ302リダイレクトされる |
| ST-AUTH-004 | CUSTOMER: 公開画面へのアクセス | 田中花子でログイン済み | 1. `{ベースURL}/catalog` へアクセスする | - | 200で商品一覧が表示される |
| ST-AUTH-005 | CUSTOMER: 会員向け画面へのアクセス | 田中花子でログイン済み | 1. `{ベースURL}/orders` へアクセスする | - | 200で自分の注文履歴が表示される |
| ST-AUTH-006 | CUSTOMER: 管理者向け画面へのアクセス | 田中花子でログイン済み | 1. `{ベースURL}/admin/releases` へアクセスする | - | 403 Forbidden が返る |
| ST-AUTH-007 | ADMIN: 公開画面へのアクセス | 管理者アカウントでログイン済み | 1. `{ベースURL}/catalog` へアクセスする | - | 200で商品一覧が表示される |
| ST-AUTH-008 | ADMIN: 会員向け画面へのアクセス | 管理者アカウントでログイン済み | 1. `{ベースURL}/cart` へアクセスする | - | 200でカート画面が表示される(管理者も会員向け画面を利用可能) |
| ST-AUTH-009 | ADMIN: 管理者向け画面へのアクセス | 管理者アカウントでログイン済み | 1. `{ベースURL}/admin/orders` へアクセスする | - | 200で注文一覧が表示される |
| ST-AUTH-010 | 匿名: REST API(`/api/**`)へのアクセス | 未ログイン状態 | 1. 認証なしで `GET /api/releases/{releaseId}`(存在するID)を呼び出す | releaseId=(登録済みRelease) | `/api/**` は `SecurityConfig` で `permitAll` のため、200でReleaseが取得できる(意図的な設計であることの確認) |

---

## 7. 並行実行・在庫競合テスト

在庫(`listings.version`)に対する楽観ロックが、複数会員の同時購入操作から二重販売を防いでいることを
検証する。既存の単体テスト`MyBatisListingOptimisticLockingTest`(2トランザクションでの競合再現)を
システムレベルのシナリオに翻訳したもの。完全な同時実行の再現には手動操作の限界があるため、
`curl`等を使った並行リクエストスクリプト、またはJMeter等の負荷ツールでの実施を推奨する。

| No | テスト項目 | 前提条件 | 手順 | 入力値 | 期待結果 |
|---|---|---|---|---|---|
| ST-CONC-001 | 同時チェックアウトによる在庫競合(在庫1) | 在庫1のUSED Listing(PUBLISHED済み)が存在する。会員A(田中花子)・会員B(佐藤次郎)それぞれのセッションで同じListingをカートに追加済み | 1. 会員A・会員Bそれぞれのセッションでチェックアウトフォームを開いておく<br>2. 可能な限り短い間隔(理想は同時)で両セッションから `POST /checkout` を送信する | 両者とも同一listingId、quantity=1 | 片方は正常に注文完了し `/orders/{orderId}` へリダイレクトされる。もう片方は `/cart` へ戻り「他の注文と同時に処理されたため確定できませんでした。もう一度お試しください。」が表示される。DB上、対象Listingに紐づくOrderは1件のみ作成され、二重販売が発生していない |
| ST-CONC-002 | 在庫が十分にある場合の同時アクセス(競合しない対照ケース) | 在庫2以上のNEW Listing(PUBLISHED済み)が存在する。会員A・会員Bそれぞれ数量1でカートに追加済み | 1. ST-CONC-001と同様に両セッションからほぼ同時にチェックアウトを送信する | 両者とも同一listingId、quantity=1(在庫2に対して2件) | 両方とも正常に注文完了する。在庫が0になり、Orderが2件作成される。エラーは発生しない |

---

## 8. 決済フローテスト

`Payment` は「Captureは常に即成功する」擬似実装(実際の決済ゲートウェイ連携なし)であるため、
決済失敗シナリオの意図的な再現手順は存在しない。この制約を前提にCapture成功系のみを検証する。

| No | テスト項目 | 前提条件 | 手順 | 入力値 | 期待結果 |
|---|---|---|---|---|---|
| ST-PAY-001 | チェックアウト連動の即時決済Capture | ログイン済み、カートに在庫ありの商品が入っている | 1. `/checkout` を正常に完了させる(ST-MEM-006と同じ手順) | ST-MEM-006と同じ | 注文確定と同時に決済Captureが行われ、注文詳細画面(`/orders/{orderId}`)でPayment状態がCAPTUREDとして確認できる |
| ST-PAY-002 | REST API経由の決済Capture(`/api/**`) | Orderが作成済み(PENDING状態、未Capture) | 1. `POST /api/orders/{orderId}/payments` を呼び出す | method=`CREDIT_CARD` | 201 Createdで`PaymentResponse`が返り、status=`CAPTURED`である |
| ST-PAY-003 | 決済情報の個別取得 | ST-PAY-001またはST-PAY-002でPaymentが作成済み | 1. `GET /api/payments/{paymentId}` を呼び出す | paymentId=(対象) | 200でPaymentの詳細(status=CAPTURED、金額、注文ID)が取得できる |
| ST-PAY-004 | 存在しないPaymentIdの取得 | - | 1. `GET /api/payments/{paymentId}` に存在しないIDを指定して呼び出す | paymentId=`nonexistent-id` | 404 Not Found が返る |

---

## 9. インフラ・非機能テスト

`record-shop-ec-cdk`の`RecordShopEcMybatisCdkStack`(VPC / RDS / ECS Fargate / ALB / CloudFront)を
対象とする。AWS環境がデプロイ済みであることを前提とする(コスト最小化のため、実施前に
`npx cdk deploy RecordShopEcMybatisCdkStack`でデプロイされていることを確認すること)。

| No | テスト項目 | 前提条件 | 手順 | 入力値 | 期待結果 |
|---|---|---|---|---|---|
| ST-INFRA-001 | CloudFront経由のHTTPS強制リダイレクト | AWS環境がデプロイ済み | 1. `http://{CloudFrontドメイン}/catalog` へアクセスする | - | CloudFrontの`viewerProtocolPolicy: REDIRECT_TO_HTTPS`設定により、HTTPS URLへリダイレクトされる |
| ST-INFRA-002 | CloudFront経由のHTTPSアクセス | AWS環境がデプロイ済み | 1. `cdk deploy` 出力の`ServiceUrl`(`https://{CloudFrontドメイン}`)へアクセスする | - | 200で商品一覧画面が正常に表示される |
| ST-INFRA-003 | ALB直接アクセス(デバッグ用URL) | AWS環境がデプロイ済み | 1. `cdk deploy` 出力の`AlbDirectUrl`(`http://{ALBのDNS名}`)へアクセスする | - | ALBからHTTP(平文)で直接アプリにアクセスでき、200で画面が表示される(オリジンの疎通確認用) |
| ST-INFRA-004 | ALBヘルスチェック | AWS環境がデプロイ済み | 1. `{ALBのDNS名}/actuator/health` へアクセスする | - | 200が返る(ALBターゲットグループのヘルスチェックもこのパスを使用しており、healthyThresholdCount=2で判定される) |
| ST-INFRA-005 | カートの非永続性(タスク再起動での消失) | ログイン済み、カートに商品を追加済み(ECS環境で実施) | 1. ECSサービスのタスクを再起動する(例: `aws ecs update-service --force-new-deployment`、または管理コンソールから)<br>2. 再起動完了後、同じブラウザで`/cart`へアクセスする | - | カート内容が空になっている(セッションがインメモリ保持のため、タスク入れ替わりで失われる)。ログインセッションも失われ再ログインが必要になる場合がある |
| ST-INFRA-006 | CDKデプロイ後のURL取得 | `record-shop-ec-cdk` の実行環境が整っている | 1. `npx cdk deploy RecordShopEcMybatisCdkStack` を実行する | - | デプロイ完了後の出力に`ServiceUrl`(CloudFront URL)と`AlbDirectUrl`が表示され、そこから最新のアクセスURLを取得できる(デプロイのたびにCloudFrontドメインが変わりうる点に留意) |
| ST-INFRA-007 | 会員登録確認メールのSES送信(AWS環境) | AWS環境がデプロイ済み。宛先メールアドレスがSESで検証済み(サンドボックスモードの制約) | 1. AWS環境上で`/register`から会員登録を行う | email=(SESで検証済みのメールアドレス) | 指定した宛先に6桁の確認コードを含むメールが実際に届く。SESサンドボックスモード中は未検証の宛先だと送信失敗する点に注意 |
| ST-INFRA-008 | mainへのpushでstageが自動更新される | featureブランチのPRが必須チェック4つ(ビルドとテスト/インフラのビルドとテスト/イメージをビルドする(push しない)/差分レビュー)を通過し、mainへsquashマージされた直後 | 1. mainへのpushで`deploy-stage.yml`が起動することを確認する<br>2. `build-and-push`ジョブがコミットSHAタグでイメージをstage ECRへpushすることを確認する<br>3. `deploy`ジョブが`cdk deploy RecordShopEcMybatisCdkStackStage --context env=stage --context imageRef=<SHA>`を実行し、`/actuator/health`のスモークが通ることを確認する | - | `deploy-stage.yml`のbuild-and-push→deployが成功し、stage環境の`AppImageRef`出力がpushしたSHAタグと一致する。スモークが`{"status":"UP"}`を返す。**2026-09-13実施、結果PASS**(確認手段: GitHub Actionsのジョブ要約、`describe-stacks --query "Stacks[0].Outputs"`) |
| ST-INFRA-009 | 壊れたイメージでサーキットブレーカーが戻す | stage環境が正常稼働中。起動に失敗する(または`/actuator/health`のヘルスチェックに通らない)壊れたコミットを用意している | 1. `workflow_dispatch`で`deploy-stage.yml`を起動し、`app_ref`に壊れたコミットのref(ブランチ/SHA)を指定する<br>2. `test-<SHA>`タグでイメージがpushされ、そのタグを`imageRef`に`cdk deploy`が実行されるのを確認する<br>3. CloudFormationのスタックイベント・ECSサービスイベントを確認する | app_ref=(壊れたコミットのref) | 新タスクが起動しない、またはタスクレベルのヘルスチェックに通らず、ECSのサーキットブレーカー(`circuitBreaker: { rollback: true }`)が働いて元のタスク定義へ自動的に戻る。CloudFormationのスタックステータスが`UPDATE_ROLLBACK_COMPLETE`になり、既存タスクは稼働を継続する(`cdk deploy`自体は失敗として終わる)。**2026-09-13実施、結果PASS**(確認手段: `describe-stacks --query "Stacks[0].StackStatus"`、ECSサービスイベント) |
| ST-INFRA-010 | タグpushが承認で止まり、承認後にprodが更新される | stageで確認済みのコミットが存在する | 1. そのコミットへタグ`v*`をpushする<br>2. `promote-prod.yml`が起動し、`production` Environmentの承認待ちで停止することを確認する<br>3. 承認者(`osidasi0005`)が承認する<br>4. 承認後にジョブが進み、`cdk deploy RecordShopEcMybatisCdkStack --context env=prod --context imageRef=sha256:...`が実行され、`/actuator/health`のスモークが通ることを確認する | tag=(stageで確認済みのコミットへの`v*`タグ) | 承認前はジョブが`production` Environmentのゲートで止まり、承認者以外は進められない。承認後にprod環境へデプロイされ、スモークが`{"status":"UP"}`を返す。**2026-09-13実施、結果PASS**(確認手段: GitHub Actionsの承認待ち画面・ジョブ要約) |
| ST-INFRA-011 | prodのイメージダイジェストがstageと一致する | ST-INFRA-010でprodへ昇格済み | 1. `promote-prod.yml`の「push したイメージのダイジェストが一致することを確かめる」ステップの結果を確認する<br>2. stage ECRとprod ECRそれぞれで対象イメージの`describe-images`を実行し、ダイジェストを突き合わせる | - | stage ECR側の対象SHAタグのダイジェストと、prod ECR側の対象`v*`タグのダイジェストが一致する。**2026-09-13実施、結果PASS**(確認手段: `describe-images --query "imageDetails[0].imageDigest"`を両アカウントで実行し比較) |
| ST-INFRA-012 | PR時点ではECRにpushされない(資格情報を取らない) | オープン中のPRが存在し、ci.ymlの必須チェックが実行されている | 1. PRを作成し、`image`ジョブ(イメージをビルドする(push しない))の実行内容を確認する<br>2. `image`ジョブにAWS認証ステップ(`aws-actions/configure-aws-credentials`等)が無いことを確認する<br>3. マージ前の時点で、stage ECRに対象コミットのSHAタグが存在しないことを確認する | - | `image`ジョブは`docker build`のみを行いAWS資格情報を取得せず、ECRへのpushは発生しない。stage ECRに該当SHAタグが現れるのはmainへのマージ後(`deploy-stage.yml`実行後)のみである。**2026-09-13実施、結果PASS**(確認手段: ci.ymlのジョブログにAWS認証ステップが無いことの確認、`describe-images`でマージ前は該当タグが無いことを確認) |

---

## 10. 付録

### 10.1 用語集

| 用語 | 説明 |
|---|---|
| Release | 「作品」。アーティスト・タイトル・ジャンル・発売年などの作品情報を持つ集約(Catalogコンテキスト) |
| Pressing | 「プレス版」。同一Releaseに対する具体的な物理盤(レーベル・品番・製造国・プレス年・媒体等)。1つのReleaseに複数のPressingが紐づく |
| Listing | 「出品」。特定のPressingに対する在庫・コンディション(NEW/USED)・価格の単位(Inventoryコンテキスト)。状態: DRAFT→PUBLISHED→RESERVED→SOLD/OUT_OF_STOCK/REMOVED |
| Cart | 「カート」。ログイン中の会員のセッションに保持される、購入前の商品の集まり(Orderingコンテキスト)。DBに永続化されない |
| Order | 「注文」。チェックアウトにより確定した注文(Orderingコンテキスト)。状態: PENDING→PAID→SHIPPED→DELIVERED、PENDING/PAID→CANCELLED |
| Payment | 「決済」。注文に対する決済(Paymentコンテキスト)。状態: PENDING→CAPTURED/FAILED/REFUNDED。本システムではCaptureは常に即成功する擬似実装 |
| Customer | 「会員」。CUSTOMER/ADMINいずれかのロールを持つ(Customerコンテキスト) |
| CUSTOMER | 一般会員ロール。商品購入・カート・注文履歴を利用できる |
| ADMIN | 出品者(管理者)ロール。`/admin/**`配下で作品・プレス版・出品の登録、注文ステータス管理を行う。自己登録不可、環境変数からの自動シードのみ |

### 10.2 既知の制約・ドキュメントとの差異

- `/register/confirm`(会員登録の確認コード入力画面)は`record-shop-ec-docs`の`05-screen-spec.md`(画面仕様書)には未記載だが、`SecurityConfig`・`EmailVerificationController`には実装済み。本仕様書ではコードを正として第3章に含めている
- 決済(Payment)は実際の決済ゲートウェイ連携を持たない擬似実装であり、意図的な決済失敗の再現手順は存在しない(第8章参照)
- Cartはセッション保持のみでDBに永続化されない。サーバー・ECSタスクの再起動でカート内容が失われる(第9章 ST-INFRA-005参照)
- AWS環境(CloudFront/ECS Fargate/RDS)は学習・デモ用途の一時環境であり、コスト最小化のため未使用時は`cdk destroy`される運用。CloudFrontドメインはデプロイのたびに変わりうるため、テスト実施前に最新の`ServiceUrl`を確認すること
- コンテキスト間(Catalog/Inventory/Ordering/Payment/Customer)はDB上でFK制約を持たずID参照のみで結合されている。システムテストにおいても、この設計に起因するデータ不整合(例: 存在しないIDの参照)が起きないことは間接的にしか検証できない点に留意する
