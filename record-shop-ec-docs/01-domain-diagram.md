# ドメイン図

レコード販売ECサイトのドメインモデルを、境界づけられたコンテキスト(Bounded Context)ごとに示す。
各コンテキストは独立した集約(Aggregate)を持ち、コンテキストをまたぐ参照は**IDのみの参照**とし、
CASCADE(親子関係の連鎖操作)はコンテキスト内で完結させる。

> このドメイン層(`domain/**`)はDB・フレームワークに一切依存しない素のJavaで実装されている。
> `Release`/`Pressing`はジャケット画像URL(`artworkUrl`)も保持し、`Release.changeArtworkUrl()`
> `Release.changePressingArtworkUrl()`で登録後の設定・変更もできる。

## コンテキスト全体図

```mermaid
graph TB
    subgraph Catalog["Catalog(カタログ)"]
        Release["Release 集約ルート<br/>作品(タイトル・アーティスト・ジャンル)"]
        Pressing["Pressing エンティティ<br/>プレス版(レーベル・品番・製造国・製造年)"]
        Release -->|"内包(CASCADE)<br/>addPressing()"| Pressing
    end

    subgraph Inventory["Inventory(在庫)"]
        Listing["Listing 集約ルート<br/>出品(New/Used・価格・在庫数)"]
    end

    subgraph Ordering["Ordering(注文)"]
        Cart["Cart 集約ルート<br/>カート(セッション保持)"]
        Order["Order 集約ルート<br/>注文(明細・配送先・請求先)"]
        OrderLine["OrderLine<br/>+ PressingSnapshot(値オブジェクト)"]
        Order -->|"内包(CASCADE)"| OrderLine
    end

    subgraph Payment["Payment(決済)"]
        PaymentAgg["Payment 集約ルート<br/>決済(金額・手段・ステータス)"]
    end

    subgraph Customer["Customer(会員)"]
        CustomerAgg["Customer 集約ルート<br/>会員(Email・パスワードハッシュ・ロール)"]
    end

    Listing -.->|"ID参照のみ(pressingId)<br/>CASCADEなし"| Pressing
    OrderLine -.->|"確定時点で複製・凍結<br/>(以後カタログの変更を追従しない)"| Pressing
    Cart -.->|"ID参照のみ(customerId)"| CustomerAgg
    Order -.->|"ID参照のみ(customerId)"| CustomerAgg
    PaymentAgg -.->|"ID参照のみ(orderId)"| Order

    classDef aggregate fill:#4a3b6b,stroke:#8b7bb8,color:#fff,stroke-width:2px
    classDef entity fill:#3a3a3a,stroke:#888,color:#fff
    class Release,Listing,Cart,Order,PaymentAgg,CustomerAgg aggregate
    class Pressing,OrderLine entity
```

実線矢印(→)は同一集約内の親子関係(CASCADE)、破線矢印(-.→)はコンテキストをまたぐID参照
(データベース上もFK制約を持たない、アプリケーションレベルの参照)を表す。

## ドメインサービスによる集約間の調停

単一の集約では守れない、複数集約にまたがる不変条件はドメインサービスが調停する。

```mermaid
graph LR
    Cart2["Cart"] --> OPS["OrderPlacementService<br/>(ドメインサービス)"]
    Listing2["Listing"] --> OPS
    Release2["Release/Pressing"] --> OPS
    OPS -->|"生成"| Order2["Order"]

    Order3["Order"] --> PCS["PaymentCaptureService<br/>(ドメインサービス)"]
    PCS -->|"生成・Capture"| Payment2["Payment"]
    PCS -->|"markPaid()"| Order3

    Email["Email重複チェック"] --> CRS["CustomerRegistrationService<br/>(ドメインサービス)"]
    CRS -->|"register()"| Customer2["Customer"]
```

- **OrderPlacementService**: カートの中身をもとに各Listingを`reserve()`(在庫予約)し、
  Release/Pressingから`PressingSnapshot`を作って`Order`を生成する。途中で例外が起きた場合は
  それまでに予約したListingを`cancelReservation()`でロールバックする(補償トランザクション)。
- **PaymentCaptureService**: `Order`の合計金額と一致する`Payment`を起票して即時Captureし、
  `Order`を`PAID`にする。
- **CustomerRegistrationService**: Email一意性チェック(Customer集約単体では強制できない、
  リポジトリ経由の重複チェックが必要)とパスワードハッシュ化を行い`Customer`を登録する。

## 設計判断の要点

- **Listing.pressingId はただのUUID参照**: Listing(Inventory)とPressing(Catalog)は別集約であり、
  在庫の増減がカタログ側の変更を引き起こしてはならないため、CASCADEもJOINも行わない
- **OrderLine.pressingSnapshot は確定時点の複製**: 後日カタログ情報が訂正されても、既存の注文内容は
  発注当時のまま変わらない(値オブジェクトなので生成後は不変)
- **Customer.role による簡易実装**: 出品者(Seller)を独立した集約にはせず、Customerに
  ADMINロールを持たせることで管理画面アクセスを表現する簡易実装(本格運用ならSeller/Staffを
  別集約に分離すべき箇所として、あえて残している)
