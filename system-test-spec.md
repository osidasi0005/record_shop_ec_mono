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

> ケース数は章3〜9のテストケース作成完了後に確定させる(現時点は `-` のプレースホルダ)。

| カテゴリコード | 章 | 対象画面・API | ケース数 |
|---|---|---|---|
| `PUB` | [3. 公開機能テスト](#3-公開機能テスト) | `/`, `/catalog`, `/catalog/{releaseId}`, `/login`, `/register`, `/register/confirm` | - |
| `MEM` | [4. 会員機能テスト](#4-会員機能テスト) | `/cart`, `/cart/add`, `/cart/remove`, `/checkout`, `/orders`, `/orders/{orderId}` | - |
| `ADM` | [5. 管理者機能テスト](#5-管理者機能テスト) | `/admin/releases/**`, `/admin/listings/**`, `/admin/orders/**` | - |
| `AUTH` | [6. 権限制御テスト](#6-権限制御テスト) | 上記全画面区分 × ロール(匿名/CUSTOMER/ADMIN) | - |
| `CONC` | [7. 並行実行・在庫競合テスト](#7-並行実行在庫競合テスト) | `/checkout`(同時アクセス時の楽観ロック) | - |
| `PAY` | [8. 決済フローテスト](#8-決済フローテスト) | `/api/orders/{orderId}/payments`, `/api/payments/{paymentId}`, チェックアウト連動 | - |
| `INFRA` | [9. インフラ・非機能テスト](#9-インフラ非機能テスト) | CloudFront/ALB/カートの非永続性/CDKデプロイ運用 | - |
| **合計** | | | **-** |

---

## 3. 公開機能テスト

認証不要でアクセスできる画面(`/`, `/catalog/**`, `/login`, `/register`, `/register/confirm`)を対象とする。

| No | テスト項目 | 前提条件 | 手順 | 入力値 | 期待結果 |
|---|---|---|---|---|---|
| ST-PUB-001 | トップページのリダイレクト | 未ログイン状態 | 1. `{ベースURL}/` へアクセスする | - | `/catalog` へ302リダイレクトされ、商品一覧が表示される |
| ST-PUB-002 | 商品一覧: 公開済み商品の表示 | PUBLISHED状態のListingを持つReleaseが1件以上存在する(サンプルデータで充足) | 1. `{ベースURL}/catalog` へアクセスする | - | PUBLISHED状態のListingを持つReleaseが一覧表示される |
| ST-PUB-003 | 商品一覧: 未公開商品の非表示 | ST-ADM-003(Listing新規作成、DRAFT状態)で作成したReleaseが存在する | 1. `{ベースURL}/catalog` へアクセスする | - | DRAFT状態のListingしか持たないReleaseは一覧に表示されない |
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

*(作成中)*

---

## 5. 管理者機能テスト

*(作成中)*

---

## 6. 権限制御テスト

*(作成中)*

---

## 7. 並行実行・在庫競合テスト

*(作成中)*

---

## 8. 決済フローテスト

*(作成中)*

---

## 9. インフラ・非機能テスト

*(作成中)*

---

## 10. 付録

*(作成中)*
