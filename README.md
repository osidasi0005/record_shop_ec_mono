# レコード販売ECサイト 仕様書

`record-shop-ec-mybatis`(DDD実装、Spring Boot + Thymeleaf + MyBatis)と`record-shop-ec-cdk`
(AWS CDK、TypeScript)からなるレコード販売ECサイトの仕様書一式。

会員登録・商品閲覧・カート・チェックアウト・決済・注文履歴・出品者向け管理画面まで揃った
ECサイトで、AWS上にCloudFront経由のHTTPSで公開できる構成になっている(DDD学習用のポートフォリオ
プロジェクト)。

**現在AWS環境は停止中**(2026-08-16にコスト都合で削除、[07-aws-teardown-record.md](07-aws-teardown-record.md))。
動かして確認したい場合はローカルで起動する。

```bash
cd record-shop-ec-mybatis && docker compose up -d --build   # http://localhost:8081
```

## 目次

1. [ドメイン図](01-domain-diagram.md) ― 境界づけられたコンテキストと集約間の参照関係
2. [ER図](02-er-diagram.md) ― RDS(PostgreSQL)上の実テーブル構造
3. [クラス図](03-class-diagram.md) ― ドメイン層の集約ルート・値オブジェクト・ドメインサービス
4. [インフラ構成図](04-infrastructure-diagram.md) ― AWS構成とCloudFront〜Fargate間のHTTPS対応の仕組み
5. [画面仕様書](05-screen-spec.md) ― 全画面のURL・入力項目・権限・実際のスクリーンショット
6. [ログインURL一覧](06-login-urls.md) ― 顧客向け・管理者向けのログイン方法
7. [AWS環境の撤収記録](07-aws-teardown-record.md) ― コスト都合での停止にあたっての状態記録・バックアップ・復元手順

## 関連リポジトリ

- [record-shop-ec-mybatis](../record-shop-ec-mybatis) ― ドメイン層・Web層(Spring Boot)+ MyBatis永続化層
- [record-shop-ec-cdk](../record-shop-ec-cdk) ― AWSインフラ定義(CDK)

## 実装のハイライト

- **在庫予約の同時実行制御**: `Listing`に楽観ロック(バージョン列)を導入し、複数ユーザーが
  同じ在庫を同時に注文確定しても二重販売が起きないことを2スレッド・2トランザクションの
  競合テストと実PostgreSQLでの同時リクエストで確認済み([02-er-diagram.md](02-er-diagram.md)参照)
- **CloudFront経由のHTTPS化**: 独自ドメイン・ACM証明書なしで、CloudFrontのデフォルトドメイン
  (`*.cloudfront.net`)を使ってHTTPS化。ALBがX-Forwarded-Protoを上書きしてしまう問題を
  CloudFront Function + カスタムヘッダーで回避している([04-infrastructure-diagram.md](04-infrastructure-diagram.md)参照)
- **境界づけられたコンテキストの厳密な分離**: Catalog/Inventory/Ordering/Payment/Customerの
  5コンテキスト間は常にID参照のみ(DB上もFK制約なし)。カタログ変更が過去の注文内容に
  影響しないよう、注文確定時点でPressing情報をスナップショットとして凍結する
  ([01-domain-diagram.md](01-domain-diagram.md)参照)
