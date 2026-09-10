# ER図

実際にRDS(PostgreSQL)上に作られるテーブル構造。ドメイン集約の境界に合わせて、**集約をまたぐ
外部キー(FK)制約は意図的に張っていない**(アプリケーションレベルでのID参照のみ)。

`schema.sql`に明示的なDDLを書き、`infrastructure/mybatis/**`のMapper(XML+Java)で
手動SQLを書く。

## テーブル一覧

```mermaid
erDiagram
    releases ||--o{ pressings : "同一集約内の親子(CASCADE)"
    releases ||--o{ release_genres : "Release自身の値の集合"
    orders ||--o{ order_lines : "Order自身の一部(非正規化)"

    releases {
        uuid id PK
        varchar title
        varchar artist_name
        int original_release_year
        varchar artwork_url "nullable"
    }
    release_genres {
        uuid release_id FK
        varchar genre
    }
    pressings {
        uuid id PK
        uuid release_id FK
        varchar label_name
        varchar catalog_number
        varchar country "length=2"
        int press_year
        varchar matrix_runout "nullable"
        boolean reissue
        varchar media_type "enum"
        varchar speed "enum"
        int disc_count
        varchar artwork_url "nullable"
    }
    listings {
        uuid id PK
        uuid pressing_id "FKなし・ID参照のみ"
        varchar condition_type "enum NEW/USED"
        decimal price_amount
        varchar price_currency "length=3"
        varchar status "enum(状態機械)"
        int stock_quantity "nullable, NEWのみ"
        varchar vinyl_grade "nullable, USEDのみ"
        varchar sleeve_grade "nullable, USEDのみ"
        varchar seller_note "nullable, USEDのみ"
        bigint version "楽観ロック(ThreadLocalで手動管理)"
    }
    orders {
        uuid id PK
        uuid customer_id "FKなし・ID参照のみ"
        varchar ship_recipient_name
        varchar ship_postal_code
        varchar ship_prefecture
        varchar ship_city
        varchar ship_address_line
        varchar ship_country
        varchar bill_recipient_name
        varchar bill_postal_code
        varchar bill_prefecture
        varchar bill_city
        varchar bill_address_line
        varchar bill_country
        varchar status "enum(状態機械)"
        timestamp placed_at
    }
    order_lines {
        uuid order_id FK
        uuid listing_id "FKなし・確定時点のスナップショット"
        varchar release_title "非正規化コピー"
        varchar artist_name "非正規化コピー"
        varchar label_name "非正規化コピー"
        varchar catalog_number "非正規化コピー"
        varchar pressing_country "非正規化コピー"
        int press_year "非正規化コピー"
        varchar media_type "非正規化コピー"
        varchar speed "非正規化コピー"
        int disc_count "非正規化コピー"
        varchar condition_type "非正規化コピー"
        varchar vinyl_grade "非正規化コピー・nullable"
        varchar sleeve_grade "非正規化コピー・nullable"
        decimal unit_price_amount
        varchar unit_price_currency
        int quantity
    }
    payments {
        uuid id PK
        uuid order_id "FKなし・ID参照のみ"
        decimal amount
        varchar currency "length=3"
        varchar method "enum"
        varchar status "enum(状態機械)"
        timestamp captured_at "nullable"
    }
    customers {
        uuid id PK
        varchar email UK
        varchar password_hash
        varchar display_name
        varchar role "enum CUSTOMER/ADMIN"
        timestamp registered_at
    }
```

## 設計判断の要点

| テーブル間の関係 | 実装 | 理由 |
|---|---|---|
| `releases` → `pressings` | `save()`内でSELECT有無判定→UPDATE時は全delete→re-insert | 同一集約内の親子。Releaseを消せばPressingも消える |
| `releases` → `release_genres` | 同上(genresも全delete→re-insert) | ジャンルはRelease自身の値の集合であり独立エンティティではない |
| `orders` → `order_lines` | 新規作成時のみinsert(order_linesは確定後不変のため更新パスなし) | OrderLineはOrder自身の一部 |
| `listings.pressing_id` | ただのUUIDカラム | Listing(Inventory)とPressing(Catalog)は別集約。CASCADE/JOINなし |
| `orders.customer_id` | ただのUUIDカラム | Order(Ordering)とCustomer(Customer)は別集約 |
| `payments.order_id` | ただのUUIDカラム | Payment(Payment)とOrder(Ordering)は別集約 |
| `order_lines`の各列 | Pressing情報を列展開して非正規化 | `PressingSnapshot`(値オブジェクト)を確定時点で複製・凍結。以後releases/pressingsが変更されても追従しない |

- **`listings.version`(楽観ロック)**: 在庫予約(`reserve()`)は「読み込み→判定→更新」の典型的な
  check-then-actであり、複数リクエストが同じListing(特にUsedの1点物)を同時に注文確定しようと
  すると、DBレベルの排他制御なしには二重販売が起こる。MyBatisにはdirty checkingが無いため、
  `MyBatisListingRepository`が`ThreadLocal`で「読み込んだ時点のversion」を手動で覚えておき、
  `UPDATE ... WHERE id=? AND version=?`文を自前で発行、0件更新(=先に他トランザクションが
  更新済み)ならSpring基底クラスの`OptimisticLockingFailureException`を投げる。
  2スレッド・2トランザクションの競合テストと実PostgreSQLでの同時リクエストで、
  二重販売が起きないことを確認済み
- **`pressings`の一意制約**: `UNIQUE(release_id, catalog_number, country, press_year)` ―
  同一作品内で品番・製造国・製造年が重複するプレス版を許さないというドメインの不変条件を
  DB制約としても保証している。
- **Address専用テーブルを作らない**: 配送先・請求先はどちらも `orders` テーブルへの埋め込み列
  (マッパーが直接列展開し、`ship_*`/`bill_*`プレフィックスで列名を分離)として表現する。
  独立集約ではなくOrder自身が持つ値オブジェクトのため。
