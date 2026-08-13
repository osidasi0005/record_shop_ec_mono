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

## 管理者向け(出品者向け管理画面)

| 項目 | 内容 |
|---|---|
| ログインURL | `{ベースURL}/login`(顧客と同じ) |
| 管理画面の入口 | `{ベースURL}/admin/releases`(作品一覧) |
| アカウントの作り方 | 自己登録では作成できない。アプリ起動時に`AdminAccountSeeder`が環境変数
  (`ADMIN_EMAIL`/`ADMIN_PASSWORD`)から1件だけ自動作成する |
| ローカル(Docker Compose)の初期アカウント | `admin@example.com` / `admin12345`(`docker-compose.yml`に平文設定、ローカル専用) |
| AWS本番環境のアカウント | メールアドレスは`admin@example.com`固定(CDKスタックの環境変数)。
  パスワードはAWS Secrets Managerで自動生成される(シークレット名: `AdminPassword...`)。
  平文をコード・ドキュメント上には置かない方針のため、必要なつどAWSコンソール
  またはCLI(`aws secretsmanager get-secret-value`)で取得する |

## デモ環境の例(AWS)

現在デプロイされているデモ環境のCloudFront URL:

```
https://d2byt2kf4uiilz.cloudfront.net
```

- トップ/商品一覧: `https://d2byt2kf4uiilz.cloudfront.net/catalog`
- ログイン: `https://d2byt2kf4uiilz.cloudfront.net/login`
- 会員登録: `https://d2byt2kf4uiilz.cloudfront.net/register`
- 管理画面: `https://d2byt2kf4uiilz.cloudfront.net/admin/releases`(要ADMINログイン)

**注意**: これは学習・デモ用途の一時的な環境であり、コスト最小化のため
`cd record-shop-ec-cdk && npx cdk destroy` で随時削除する運用としている。
削除後はこのURLが無効になるため、再デプロイ後は`cdk deploy`の出力
(`RecordShopEcCdkStack.ServiceUrl`)から都度最新のURLを確認すること
(CloudFrontのドメイン名はデプロイのたびに変わりうる)。
