# レコード販売ECサイト 仕様書

`record-shop-ec-jpa`(DDD実装、Spring Boot + Thymeleaf)と`record-shop-ec-cdk`
(AWS CDK、TypeScript)からなるレコード販売ECサイトの仕様書一式。

会員登録・商品閲覧・カート・チェックアウト・決済・注文履歴・出品者向け管理画面まで揃った
ECサイトとして、AWS上にCloudFront経由のHTTPSで公開されている(DDD学習用のポートフォリオ
プロジェクト)。

**永続化層違いの比較版として`record-shop-ec-mybatis`が存在する。** ドメイン層・Web層は
`record-shop-ec-jpa`(JPA版)から無修正コピーしており、永続化層だけをMyBatisで
再実装した(JPA vs MyBatisの技術比較が目的)。そのため本ドキュメント一式(ドメイン図・
クラス図・画面仕様書等)は**基本的にMyBatis版にもそのまま当てはまる**。永続化技術に
依存する箇所(ER図・インフラ構成図)のみ、両バージョンの違いを明記している。

**例外**: ジャケット画像URL(`artworkUrl`)の登録・変更・表示機能は、比較実験の枠を超える
アプリケーション機能として**`record-shop-ec-mybatis`にのみ**追加した。ドメイン層
(`Release`/`Pressing`)・永続化層(`releases`/`pressings`テーブル)・Web層(管理画面・
カタログ画面)にまたがる差分のため、該当箇所は各ドキュメント内で個別に注記している。

## 目次

1. [ドメイン図](01-domain-diagram.md) ― 境界づけられたコンテキストと集約間の参照関係(JPA版・MyBatis版共通)
2. [ER図](02-er-diagram.md) ― RDS(PostgreSQL)上の実テーブル構造(JPA版・MyBatis版共通の物理構造+実現方法の違い)
3. [クラス図](03-class-diagram.md) ― ドメイン層の集約ルート・値オブジェクト・ドメインサービス(JPA版・MyBatis版共通)
4. [インフラ構成図](04-infrastructure-diagram.md) ― AWS構成とCloudFront〜Fargate間のHTTPS対応の仕組み(JPA版・MyBatis版それぞれのスタック)
5. [画面仕様書](05-screen-spec.md) ― 全画面のURL・入力項目・権限・実際のスクリーンショット(Web層は基本的にJPA版・MyBatis版で同一。ジャケット画像機能はMyBatis版のみ)
6. [ログインURL一覧](06-login-urls.md) ― 顧客向け・管理者向けのログイン方法

## 関連リポジトリ

- [record-shop-ec-jpa](../record-shop-ec-jpa) ― ドメイン層・Web層(Spring Boot)+ JPA永続化層
- [record-shop-ec-mybatis](../record-shop-ec-mybatis) ― 同ドメイン層・Web層 + MyBatis永続化層(比較実験用)
- [record-shop-ec-cdk](../record-shop-ec-cdk) ― AWSインフラ定義(CDK)。JPA版・MyBatis版それぞれ独立したスタックを持つ

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
