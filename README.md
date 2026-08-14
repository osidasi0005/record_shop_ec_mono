# record-shop-ec-mybatis

[record-shop-ec-domain](../record-shop-ec-domain)(JPA版)からドメイン層・Web層をそのままコピーし、
永続化層だけをMyBatisで実装した比較実験用プロジェクト。JPA vs MyBatisの体感差を、
同じドメインモデル・同じ問題設定(N+1回避、楽観ロック)の上で比較することが目的。

## スコープ

- **ドメイン層(`domain/`)は record-shop-ec-domain と完全に同一**(無修正コピー)。
  Catalog(Release/Pressing/genres)・Inventory(Listing)・Ordering(Cart/Order/OrderLine)・
  Payment(Payment)・Customer(Customer)の全5集約
- **Web層(`web/`・`config/`・`infrastructure/security`)もJPA版から無修正コピー**。
  REST API・Thymeleaf画面(カタログ/カート/チェックアウト/注文履歴/管理画面/会員登録・ログイン)・
  Spring Securityによるフォーム認証・ROLE_ADMIN制御まで同等に動作する
- **`BCryptPasswordHasher`のみ実際にSpring Security依存で実装**(比較実験の枠を超えるため
  当初は見送っていたが、Web層を追加した時点でログイン機能に必須となり実装した)

永続化層(`infrastructure/mybatis/`)だけがJPA版と異なる。

## JPA版との違い(実装して分かったこと)

- **UUID型の変換**: JPAは自動で扱うが、MyBatisはデフォルトでUUID用のTypeHandlerを持たない。
  [`UuidTypeHandler`](src/main/java/com/example/recordshop/infrastructure/mybatis/UuidTypeHandler.java)を自作した
- **プリミティブ型のjavaType別名**: MyBatisは`int`/`long`/`boolean`という別名がラッパー型
  (`Integer`/`Long`/`Boolean`)を指す。recordのprimitiveフィールドと一致させるには
  `_int`/`_long`/`_boolean`という別名を使う必要がある(要求バージョン: MyBatis 3.5.17で確認)
- **N+1の回避**: JPAの`@OneToMany`/`@ElementCollection`のような自動組み立ては無い。
  `findAll()`/`findByCustomerId()`系のメソッドで、子コレクションを「全件(またはIN句で対象分)を
  1回のSELECTで取得してJava側でグルーピングする」設計にしないと、JPA版と同じN+1問題が
  そのまま再現する(Release/Order双方で同じ原則を適用)
- **楽観ロック**: JPAの`@Version`は永続化コンテキストが自動管理する。MyBatisにはその仕組みが
  無いため、[`MyBatisListingRepository`](src/main/java/com/example/recordshop/infrastructure/mybatis/MyBatisListingRepository.java)
  で`ThreadLocal`を使い「読み込んだ時点のversion」を手動で覚えておく実装が必要だった
- **dirty checking(変更検知)が無い**: JPAは「新規か更新か」「子コレクションに何が増えたか」を
  自動判定するが、MyBatisには無い。`MyBatisReleaseRepository`/`MyBatisOrderRepository`の
  `save()`は、SELECTで既存有無を判定してINSERT/UPDATEに振り分け、子コレクション
  (genres/pressings)は全delete→re-insertで最新化する設計にしている。この実装漏れは、
  実際にAPI経由で「既存Releaseへのpressing追加」「決済確定によるOrderステータス更新」を
  動かして初めて発覚した(主キー重複エラー) — ユニットテストだけでは気づけなかった典型例
- **例外の違い**: JPA版の`ApiExceptionHandler`/`CheckoutController`はHibernate固有の
  `ObjectOptimisticLockingFailureException`を捕捉するが、MyBatis版は基底クラスの
  `OptimisticLockingFailureException`を直接投げる実装にしたため、そちらを捕捉するよう変更した
  (結果的に特定の永続化技術に依存しない、より汎用的な書き方になった)

## ビルド・テスト

```bash
./mvnw test
```

Dockerが無くてもH2(PostgreSQL互換モード)でテストが通る(record-shop-ec-domainと同じ方針)。全42テスト。

## ローカル起動

record-shop-ec-domain(JPA版、app:8080/postgres:5432)と同時起動できるよう、ポート・DB名をずらしている。

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
