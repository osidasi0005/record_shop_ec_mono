# record-shop-ec-spec — レコード販売EC 再現用 Claude Code 指示書

アナログレコード販売ECサイト(DDD学習・ポートフォリオ)を、**まっさらな環境から Claude Code だけで再作成する**ための指示書一式。
成果物は以下の4つの Git リポジトリ(GitHub private)。

| リポジトリ | 内容 |
|---|---|
| `record-shop-ec-mybatis` | Spring Boot 3.5 / Java 21 / MyBatis / Thymeleaf / Spring Security のアプリ本体(ドメイン層5コンテキスト + REST API + 画面 + Docker Compose) |
| `record-shop-ec-cdk` | AWS CDK(TypeScript)。VPC + RDS PostgreSQL + ECS Fargate + ALB + CloudFront + Secrets Manager + SES の1スタック |
| `record-shop-ec-docs` | 設計ドキュメント(ドメイン図・ER図・クラス図・インフラ構成図・画面仕様書・ログインURL一覧、Mermaid + スクリーンショット) |
| `record-shop-ec-tests` | システムテスト仕様書(70ケース)と実施記録 |

## ファイル構成

| ファイル | 役割 |
|---|---|
| [00-common.md](00-common.md) | **全フェーズ共通の前提**(何を作るか・プレースホルダ・技術スタック・コード規約・用語・アーキテクチャ)。毎回最初に読ませる |
| [phase-1-domain.md](phase-1-domain.md) | mybatis: 骨格 + ドメイン層(5コンテキスト + shared)+ InMemoryリポジトリ + 単体テスト60件 |
| [phase-2-persistence.md](phase-2-persistence.md) | mybatis: schema.sql + MyBatis永続化層 + application.yml + H2結合テスト + 楽観ロック競合テスト(累計77件) |
| [phase-3-web.md](phase-3-web.md) | mybatis: REST API + Thymeleaf画面 + Spring Security + CheckoutService + SESアダプタ + フィルタ + PageRenderingTest(累計93件) |
| [phase-4-finish-app.md](phase-4-finish-app.md) | mybatis: SampleDataSeeder + アートワークSVG + Dockerfile/compose + README + GitHub push |
| [phase-5-cdk.md](phase-5-cdk.md) | cdk: スタック定義 + テスト5件 + デプロイ/destroy手順 + GitHub push |
| [phase-6-docs.md](phase-6-docs.md) | docs: Markdown 7本 + Mermaid 12図 + スクリーンショット13枚 + GitHub push |
| [phase-7-tests.md](phase-7-tests.md) | tests: システムテスト仕様書70ケース + 実施記録 + GitHub push |

## 事前準備

1. 実行環境に以下を用意する: Java 21、Docker Desktop、Node.js 20+、AWS CLI v2(認証済み)、`gh` CLI(認証済み)、git
2. `00-common.md` 0.2 のプレースホルダ(`{GITHUB_OWNER}`, `{MAIL_FROM_ADDRESS}`, `{AWS_REGION}`, `{AWS_PROFILE}`)を決める。指示書を Claude Code に渡す前に **これらを実値に置換したコピー**を作るか、プロンプトの冒頭で「`{MAIL_FROM_ADDRESS}` は xxx@example.com と読み替える」と伝える
3. `{MAIL_FROM_ADDRESS}` を AWS SES で送信検証しておく(phase-3 で会員登録メールを試すまでに)
4. 4リポジトリの親フォルダを1つ決める(例 `C:\work\record-shop`)。**すべて兄弟ディレクトリに置く**

## 実行手順(Claude Code への投げ方)

各フェーズは「新しいセッションで、対象リポジトリのフォルダを開き、`00-common.md` と該当 phase ファイルを渡す」だけで完結するように書いてある。

```
<親フォルダ>/record-shop-ec-mybatis   ← phase-1 〜 phase-4(同じフォルダで続ける)
<親フォルダ>/record-shop-ec-cdk       ← phase-5
<親フォルダ>/record-shop-ec-docs      ← phase-6
<親フォルダ>/record-shop-ec-tests     ← phase-7
```

プロンプトの例(phase-1):

> `<spec のパス>/00-common.md` と `<spec のパス>/phase-1-domain.md` を読み、phase-1 を実施してください。
> プレースホルダは次のとおりです: `{GITHUB_OWNER}`=..., `{MAIL_FROM_ADDRESS}`=..., `{AWS_REGION}`=ap-northeast-1。
> 完了条件をすべて満たしたら、実行したテスト件数と確認結果を報告してください。

続きのフェーズも同じ形式で `phase-2-persistence.md` … と順に渡す。同一セッションで続けてもよいが、コンテキストが長くなるためフェーズごとに新規セッションを推奨。

各フェーズ末尾の「完了条件」を人間側でも確認してから次へ進む(特に `./mvnw test` の件数、ブラウザでの動作確認、`gh repo view`)。

## フェーズ一覧と完了条件(要約)

| フェーズ | リポジトリ | 主な成果 | 完了条件 |
|---|---|---|---|
| 1 | mybatis | ドメイン層 + InMemory + 単体テスト | `./mvnw test` 60件 green、domain に Spring 依存なし |
| 2 | mybatis | schema.sql + MyBatis + 結合テスト | 累計77件 green、PostgreSQL 起動で9テーブル作成 |
| 3 | mybatis | Web層一式 + PageRenderingTest | 累計93件 green、ブラウザで登録→購入→管理操作が通る、権限マトリクスどおり |
| 4 | mybatis | Seeder + Docker + README + push | `docker compose up` で `localhost:8081/catalog` に4作品、管理者ログイン可、GitHub private |
| 5 | cdk | スタック + テスト + push | `npm test` 5件、`cdk synth` 成功、(任意)deploy 後 `ServiceUrl` が HTTPS で開く |
| 6 | docs | 設計ドキュメント + 画像 + push | 7ファイル + 13画像、GitHub 上で全 Mermaid が描画 |
| 7 | tests | 仕様書70ケース + 実施記録 + push | ケース数一致、実施記録1件以上 |

## 元プロジェクトからの意図的な差分

この指示書は元プロジェクトの最終状態を「設計レベル」で再現するが、以下は**改善済みの姿**で書いてある:

- チェックアウトの決済確定後に `Listing#confirmSale` を呼び USED 出品を SOLD にする / 注文キャンセルで `cancelReservation` を呼び在庫を戻す(元プロジェクトでは未接続だった)
- 売り切れ品を含むカートで「売り切れのため確定できませんでした: {作品名}」と表示し、カート表示時に売り切れ行を自動削除する(内部例外メッセージをそのまま出さない)
- 画面仕様書に `/register/confirm` を含める、テスト仕様書の入力値を実装の Enum に合わせる(`LP`/`RPM_33`/`JP`)
- 個人情報・AWS アカウント固有値はプレースホルダ化

## コストに関する注意

phase-5 でデプロイすると **月 $60〜90 程度**(NAT Gateway + RDS + ALB + CloudFront)が稼働中ずっと発生する。検証が済んだら必ず
`npx cdk destroy RecordShopEcMybatisCdkStack` で削除すること。DB は起動のたびに初期化される設計なのでデータ消失は気にしなくてよい。
