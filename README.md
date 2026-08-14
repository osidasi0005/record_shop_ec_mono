# record-shop-ec-mybatis

DDD学習用のレコード販売ECサイト。ドメイン層(Catalog/Inventory/Ordering/Payment/Customerの
全5集約)・Web層(REST API・Thymeleaf画面・Spring Securityによるフォーム認証)・
永続化層(MyBatis)で構成する。

## スコープ

- **ドメイン層(`domain/`)**: Catalog(Release/Pressing/genres)・Inventory(Listing)・
  Ordering(Cart/Order/OrderLine)・Payment(Payment)・Customer(Customer)の全5集約
- **Web層(`web/`・`config/`・`infrastructure/security`)**: REST API・Thymeleaf画面
  (カタログ/カート/チェックアウト/注文履歴/管理画面/会員登録・ログイン)・
  Spring Securityによるフォーム認証・ROLE_ADMIN制御まで動作する
- **永続化層(`infrastructure/mybatis/`)**: MyBatisで実装

## 実装のポイント(MyBatisで工夫した点)

- **UUID型の変換**: MyBatisはデフォルトでUUID用のTypeHandlerを持たない。
  [`UuidTypeHandler`](src/main/java/com/example/recordshop/infrastructure/mybatis/UuidTypeHandler.java)を自作した
- **プリミティブ型のjavaType別名**: MyBatisは`int`/`long`/`boolean`という別名がラッパー型
  (`Integer`/`Long`/`Boolean`)を指す。recordのprimitiveフィールドと一致させるには
  `_int`/`_long`/`_boolean`という別名を使う必要がある(要求バージョン: MyBatis 3.5.17で確認)
- **N+1の回避**: MyBatisには集約の自動組み立てが無いため、`findAll()`/`findByCustomerId()`系の
  メソッドで、子コレクションを「全件(またはIN句で対象分)を1回のSELECTで取得してJava側で
  グルーピングする」設計にしないとN+1問題が発生する(Release/Order双方で同じ原則を適用)
- **楽観ロック**: MyBatisには永続化コンテキストによる自動管理が無いため、
  [`MyBatisListingRepository`](src/main/java/com/example/recordshop/infrastructure/mybatis/MyBatisListingRepository.java)
  で`ThreadLocal`を使い「読み込んだ時点のversion」を手動で覚えておく実装が必要だった
- **dirty checking(変更検知)が無い**: 「新規か更新か」「子コレクションに何が増えたか」を
  自動判定する仕組みが無いため、`MyBatisReleaseRepository`/`MyBatisOrderRepository`の
  `save()`は、SELECTで既存有無を判定してINSERT/UPDATEに振り分け、子コレクション
  (genres/pressings)は全delete→re-insertで最新化する設計にしている。この実装漏れは、
  実際にAPI経由で「既存Releaseへのpressing追加」「決済確定によるOrderステータス更新」を
  動かして初めて発覚した(主キー重複エラー) — ユニットテストだけでは気づけなかった典型例
- **楽観ロック競合の例外**: `ApiExceptionHandler`/`CheckoutController`はSpring基底クラスの
  `OptimisticLockingFailureException`を捕捉する設計にしており、特定の永続化技術に
  依存しない汎用的な書き方になっている

## ビルド・テスト

```bash
./mvnw test
```

Dockerが無くてもH2(PostgreSQL互換モード)でテストが通る。全48テスト。

## ローカル起動

```bash
# アプリ+実PostgreSQLを一括起動(http://localhost:8081)
docker compose up -d --build

# 動作確認
curl -X POST http://localhost:8081/api/releases \
  -H "Content-Type: application/json" \
  -d '{"title":"Kind of Blue","artistName":"Miles Davis","genres":["Jazz"],"originalReleaseYear":1959}'

# 停止
docker compose down
```

DB(PostgreSQL、5433番ポート)だけ起動してローカルの`./mvnw spring-boot:run`で動かすことも可能。
