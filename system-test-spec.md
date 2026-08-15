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

*(章3〜9のテストケース作成完了後、件数集計表をここに記載する。作業中は仮のプレースホルダとする。)*

---

## 3. 公開機能テスト

*(作成中)*

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
