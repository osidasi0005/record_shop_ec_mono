# ログインURL一覧

## 共通事項

顧客(CUSTOMER)と管理者(ADMIN)で**ログイン画面のURLは同じ**。ロールごとに専用のログインURLは
用意していない設計(ログイン後、ナビゲーションの表示や`/admin/**`へのアクセス可否がロールに
応じて変わるだけ)。

## 顧客向け

| 項目 | 内容 |
|---|---|
| ログインURL | `{ベースURL}/login` |
| 会員登録URL | `{ベースURL}/register` |
| ログイン後の遷移先 | `/catalog`(商品一覧) |
| ログアウト | ナビゲーションの「ログアウト」リンク(`POST /logout`相当、Spring Security標準) |
| 認証方式 | メールアドレス + パスワード(セッションCookie方式) |

新規に試したい場合は`/register`から自由にアカウントを作成できる。

### サンプルデータの顧客アカウント

アプリ起動時に`SampleDataSeeder`(`infrastructure.devdata`)が自動投入するサンプル顧客4名。
全員ロールはCUSTOMER、パスワードは共通。

| 表示名 | メールアドレス(ログインID) | パスワード |
|---|---|---|
| 田中 花子 | `tanaka.hanako@example.com` | `Passw0rd!2024` |
| 佐藤 次郎 | `sato.jiro@example.com` | `Passw0rd!2024` |
| 鈴木 美咲 | `suzuki.misaki@example.com` | `Passw0rd!2024` |
| 高橋 健太 | `takahashi.kenta@example.com` | `Passw0rd!2024` |

各アカウントには異なる状態の注文が1件ずつ紐づいており、ログイン後`/orders`から確認できる。

| アカウント | 注文の状態 |
|---|---|
| 田中 花子 | Thriller(Used)を注文、銀行振込未払いのまま(Order: PENDING / Payment: PENDING) |
| 佐藤 次郎 | Selected Ambient Works 85-92(Used)を注文、決済完了(Order: PAID / Payment: CAPTURED) |
| 鈴木 美咲 | 複数明細(NEW×2 + USED×1)を注文、発送済み(Order: SHIPPED / Payment: CAPTURED) |
| 高橋 健太 | Nevermind(NEW)を注文後に返金・キャンセル(Order: CANCELLED / Payment: REFUNDED) |

## 管理者向け(出品者向け管理画面)

| 項目 | 内容 |
|---|---|
| ログインURL | `{ベースURL}/login`(顧客と同じ) |
| 管理画面の入口 | `{ベースURL}/admin/releases`(作品一覧) |
| アカウントの作り方 | 自己登録では作成できない。アプリ起動時に`AdminAccountSeeder`が環境変数
  (`ADMIN_EMAIL`/`ADMIN_PASSWORD`)から1件だけ自動作成する |
| ローカル(Docker Compose)の初期アカウント | `admin@example.com` / `Passw0rd!2024`(`docker-compose.yml`に平文設定、ローカル専用。サンプル顧客アカウントとパスワードを統一している) |
| AWS本番環境のアカウント | メールアドレスは`admin@example.com`固定(CDKスタックの環境変数)。
  パスワードはAWS Secrets Managerで自動生成される(シークレット名: `AdminPassword...`)。
  平文をコード・ドキュメント上には置かない方針のため、必要なつどAWSコンソール
  またはCLI(`aws secretsmanager get-secret-value`)で取得する |

## アクセス先の一覧

`{ベースURL}`は環境によって変わる。現在アクセスできるのは**ローカル環境のみ**。

| 環境 | ベースURL | 状態 |
|---|---|---|
| ローカル(Docker Compose) | `http://localhost:8081` | `docker compose up -d --build`で起動 |
| AWS(`RecordShopEcMybatisCdkStack`) | デプロイのたびに変わる | **2026-08-16に削除済み(停止中)** |

| 画面 | パス | 権限 |
|---|---|---|
| トップ/商品一覧 | `/catalog` | 誰でも |
| ログイン | `/login` | 誰でも |
| 会員登録 | `/register` | 誰でも |
| 管理画面(作品一覧) | `/admin/releases` | 要ADMIN |
| 管理画面(注文一覧) | `/admin/orders` | 要ADMIN |

### AWS環境について

コスト最小化のため、使わない期間は削除する運用としている。2026-08-16にコスト都合で削除した
(経緯・DBのバックアップ・復元手順は[07-aws-teardown-record.md](07-aws-teardown-record.md)を参照)。
かつて使っていた`https://d10qc58jhjv0fq.cloudfront.net`は**現在無効**。

CloudFrontのドメイン名はデプロイのたびに変わるため、再デプロイ後は`cdk deploy`の出力
(`ServiceUrl`)から都度最新のURLを確認すること。

```bash
cd record-shop-ec-cdk
npx cdk deploy RecordShopEcMybatisCdkStack   # (再)デプロイ
npx cdk destroy RecordShopEcMybatisCdkStack  # 削除
```

### ローカル環境の制約

`/register`の会員登録は、確認コードのメール送信にAWS SESを使う実装のため**ローカルでは完了できない**
(Docker ComposeがAWS認証情報を渡しておらず、送信元アドレスの検証もスタック削除で解除されている)。
ローカルで動作を試す場合は、上記のサンプル顧客アカウントでログインすること。
