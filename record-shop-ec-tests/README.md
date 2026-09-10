# record-shop-ec-tests

レコード販売ECサイトのシステムテスト仕様書、および将来的なテストコード・実施記録を管理するリポジトリ。

## 対象システム

| リポジトリ | 役割 |
|---|---|
| [record-shop-ec-mybatis](https://github.com/osidasi0005/record-shop-ec-mybatis) | バックエンドAPI + Thymeleaf画面(Spring Boot / MyBatis) |
| [record-shop-ec-cdk](https://github.com/osidasi0005/record-shop-ec-cdk) | AWSインフラ定義(CDK) |
| [record-shop-ec-docs](https://github.com/osidasi0005/record-shop-ec-docs) | ドメインモデル図・ER図・画面仕様書などの設計ドキュメント |

## ドキュメント

- [system-test-spec.md](system-test-spec.md) — システムテスト仕様書

## テスト実施記録

| 実施日 | 環境 | 対象コミット | 結果 |
|---|---|---|---|
| [2026-08-16](test-results/2026-08-16-aws.md) | AWS | mybatis `a226476` | Pass 50 / 条件付き 5 / Fail 11 / Blocked 2 |
| [2026-08-16(修正版で再実施)](test-results/2026-08-16-aws-retest.md) | AWS | mybatis `701dd9d` | Pass 62 / 条件付き 5 / Fail 0 / Blocked 1 |

## テスト実行環境の前提

以下いずれかの環境でテストを実施できる。

- **ローカル(Docker Compose)**: `record-shop-ec-mybatis` の `docker compose up` で起動。管理者アカウントは `docker-compose.yml` に設定済み。
- **AWS(CloudFront経由)**: `record-shop-ec-cdk` で `npx cdk deploy RecordShopEcMybatisCdkStack` してデプロイした環境。学習・デモ用途のためコスト最小化で必要な時のみ起動し、URLはデプロイのたびに変わる(`cdk deploy` 出力の `ServiceUrl` を参照)。

いずれの環境かはテスト実施記録側で明記すること。仕様書内では `{ベースURL}` をプレースホルダとして使用する。
