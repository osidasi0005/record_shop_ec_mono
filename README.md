# record-shop-ec-mybatis

[record-shop-ec-domain](../record-shop-ec-domain)(JPA版)からドメイン層をそのままコピーし、
永続化層だけをMyBatisで実装した比較実験用プロジェクト。JPA vs MyBatisの体感差を、
同じドメインモデル・同じ問題設定(N+1回避、楽観ロック)の上で比較することが目的。

## スコープ

比較に必要な範囲のみを移植している(全集約・web層は対象外)。

- **Catalog**(`Release` / `Pressing` / genres) — ネストしたコレクションの組み立て方の比較用
- **Inventory**(`Listing`) — 楽観ロック(同時実行制御)の実装方法の比較用

`domain/` 配下は record-shop-ec-domain と完全に同一(無修正コピー)。ドメイン層が
永続化技術に依存していないことの裏付けでもある。

## JPA版との違い(実装して分かったこと)

- **UUID型の変換**: JPAは自動で扱うが、MyBatisはデフォルトでUUID用のTypeHandlerを持たない。
  [`UuidTypeHandler`](src/main/java/com/example/recordshop/infrastructure/mybatis/UuidTypeHandler.java)を自作した
- **プリミティブ型のjavaType別名**: MyBatisは`int`/`long`/`boolean`という別名がラッパー型
  (`Integer`/`Long`/`Boolean`)を指す。recordのprimitiveフィールドと一致させるには
  `_int`/`_long`/`_boolean`という別名を使う必要がある(要求バージョン: MyBatis 3.5.17で確認)
- **N+1の回避**: JPAの`@OneToMany`/`@ElementCollection`のような自動組み立ては無い。
  [`MyBatisReleaseRepository.findAll()`](src/main/java/com/example/recordshop/infrastructure/mybatis/MyBatisReleaseRepository.java)
  で、genres/pressingsを「全件を1回のSELECTで取得してJava側でグルーピングする」設計にしないと、
  JPA版と同じN+1問題がそのまま再現する
- **楽観ロック**: JPAの`@Version`は永続化コンテキストが自動管理する。MyBatisにはその仕組みが
  無いため、[`MyBatisListingRepository`](src/main/java/com/example/recordshop/infrastructure/mybatis/MyBatisListingRepository.java)
  で`ThreadLocal`を使い「読み込んだ時点のversion」を手動で覚えておく実装が必要だった

## ビルド・テスト

```bash
./mvnw test
```

Dockerが無くてもH2(PostgreSQL互換モード)でテストが通る(record-shop-ec-domainと同じ方針)。

## ローカルDB起動(任意)

record-shop-ec-domain(JPA版、5432番ポート)と同時起動できるようポート・DB名をずらしている。

```bash
docker compose up -d
```
