# レコード販売ECサイト 仕様書

`record-shop-ec-domain`(DDD実装、Spring Boot + Thymeleaf)と`record-shop-ec-cdk`
(AWS CDK、TypeScript)からなるレコード販売ECサイトの仕様書一式。

会員登録・商品閲覧・カート・チェックアウト・決済・注文履歴・出品者向け管理画面まで揃った
ECサイトとして、AWS上にCloudFront経由のHTTPSで公開されている(DDD学習用のポートフォリオ
プロジェクト)。

## 目次

1. [ドメイン図](01-domain-diagram.md) ― 境界づけられたコンテキストと集約間の参照関係
2. [ER図](02-er-diagram.md) ― RDS(PostgreSQL)上の実テーブル構造
3. [クラス図](03-class-diagram.md) ― ドメイン層の集約ルート・値オブジェクト・ドメインサービス
4. [インフラ構成図](04-infrastructure-diagram.md) ― AWS構成とCloudFront〜Fargate間のHTTPS対応の仕組み
5. [画面仕様書](05-screen-spec.md) ― 全画面のURL・入力項目・権限・実際のスクリーンショット
6. [ログインURL一覧](06-login-urls.md) ― 顧客向け・管理者向けのログイン方法

## 関連リポジトリ

- [record-shop-ec-domain](../record-shop-ec-domain) ― ドメイン層・Web層(Spring Boot)
- [record-shop-ec-cdk](../record-shop-ec-cdk) ― AWSインフラ定義(CDK)

## 実装のハイライト

- **在庫予約の同時実行制御**: `Listing`に楽観ロック(`@Version`)を導入し、複数ユーザーが
  同じ在庫を同時に注文確定しても二重販売が起きないことを2スレッド・2トランザクションの
  競合テストと実PostgreSQLでの同時リクエストで確認済み([02-er-diagram.md](02-er-diagram.md)参照)
- **CloudFront経由のHTTPS化**: 独自ドメイン・ACM証明書なしで、CloudFrontのデフォルトドメイン
  (`*.cloudfront.net`)を使ってHTTPS化。ALBがX-Forwarded-Protoを上書きしてしまう問題を
  CloudFront Function + カスタムヘッダーで回避している([04-infrastructure-diagram.md](04-infrastructure-diagram.md)参照)
- **境界づけられたコンテキストの厳密な分離**: Catalog/Inventory/Ordering/Payment/Customerの
  5コンテキスト間は常にID参照のみ(DB上もFK制約なし)。カタログ変更が過去の注文内容に
  影響しないよう、注文確定時点でPressing情報をスナップショットとして凍結する
  ([01-domain-diagram.md](01-domain-diagram.md)参照)
