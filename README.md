# record_shop_ec_mono

DDD 学習用レコード販売 EC サイトの 5 リポジトリを 1 つにまとめたモノリポ。元リポジトリ（osidasi0005/record-shop-ec-mybatis, -cdk, -docs, -tests, -spec）は変更せず、`git subtree add` で履歴ごと取り込んだ（2026-09-10）。

## 構成

| ディレクトリ | 元リポジトリ | 内容 |
| --- | --- | --- |
| `record-shop-ec-mybatis` | osidasi0005/record-shop-ec-mybatis | アプリ本体（Spring Boot 3.5 / Java 21 / MyBatis / Thymeleaf / Spring Security、Docker Compose 付き） |
| `record-shop-ec-cdk` | osidasi0005/record-shop-ec-cdk | AWS インフラ定義（CDK v2 / TypeScript、VPC+RDS+ECS Fargate+ALB+CloudFront） |
| `record-shop-ec-docs` | osidasi0005/record-shop-ec-docs | 設計ドキュメント（ドメイン図・ER図・クラス図・インフラ図・画面仕様・ログインURL・AWS撤収記録） |
| `record-shop-ec-tests` | osidasi0005/record-shop-ec-tests | システムテスト仕様書（70ケース）と実施記録 |
| `record-shop-ec-spec` | osidasi0005/record-shop-ec-spec | Claude Code で再現するための指示書（phase-1〜7） |

## ディレクトリ名が元のリポジトリ名のままである理由

`record-shop-ec-docs` が `../record-shop-ec-mybatis/...` へ相対リンクしているため。兄弟配置を保てば無修正で動く。

## ローカル起動

```
cd record-shop-ec-mybatis && docker compose up -d --build
```

→ http://localhost:8081

## AWS デプロイ

普段は手動でデプロイしない。CI/CD で自動的に反映される:

- `main` への push → `deploy-stage.yml` がイメージを ECR へ push し、stage アカウントへ自動デプロイ
- コミットに `v*` タグを push → `promote-prod.yml` が承認ゲートを経て、stage で確認済みのイメージ(ダイジェスト指定)をそのまま本番へ昇格(ビルドはしない)

手動で流すのは初期構築の検証や、インフラ定義だけを変えた確認のとき。`env`(`prod`|`stage`)と `imageRef`(ECR に push 済みの SHA タグ、または `sha256:` ダイジェスト)のコンテキストがどちらも必須:

```
cd record-shop-ec-cdk && npm ci
npx cdk deploy RecordShopEcMybatisCdkStackStage --context env=stage --context imageRef=<sha>
```

詳細(スタック構成、コンテキストの意味、destroy の手順)は `record-shop-ec-cdk/README.md`、AWS 側の下準備(ECR・OIDC ロール)は `record-shop-ec-cdk/infra/README.md` を参照。CI/CD の型の出典は https://github.com/osidasi0005/cicd_playbook 。

月 $60〜90 かかるので使わないときは `npx cdk destroy <スタック名> --context env=<env> --context imageRef=<任意の文字列>`

## 詳細

各ディレクトリの詳細はそれぞれの README.md を参照。
