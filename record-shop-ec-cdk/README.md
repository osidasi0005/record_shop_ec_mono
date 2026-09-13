# record-shop-ec-cdk

[record-shop-ec-mybatis](../record-shop-ec-mybatis)をAWS上に稼働させるためのCDK(TypeScript)プロジェクト。

## スタック構成

| 環境 | スタック名 | AWSアカウント |
|---|---|---|
| prod | `RecordShopEcMybatisCdkStack` | 本番用アカウント |
| stage | `RecordShopEcMybatisCdkStackStage` | 検証用アカウント(prodとは別) |

VPC + NATゲートウェイ1個 + RDS PostgreSQL db.t4g.micro + ECS Fargate 0.25vCPU/0.5GB x1台 + ALB + CloudFront。
prod と stage で構成は同一で、違うのはスタック名とIAMロール名だけ。

**イメージはこのスタックではビルドしない。** ECS タスクは ECR の既存イメージ
(`record-shop-ec-mybatis` リポジトリ。このスタックの外、`infra/ecr-repository.yaml` で先に作る)を
`--context imageRef=<SHAタグ または sha256:ダイジェスト>` で指定して参照する。
デプロイ前にそのイメージを ECR へ push しておくこと。

**コスト**: 月$60〜90程度(NAT + RDS + ALB + CloudFront)かかり続けるため、使わなくなったら
`npx cdk destroy <スタック名>` で削除すること。

## デプロイ手順

```sh
# stage へデプロイする例(SHA タグを指定)
npx cdk deploy RecordShopEcMybatisCdkStackStage --context env=stage --context imageRef=<sha>

# prod へデプロイする例
npx cdk deploy RecordShopEcMybatisCdkStack --context env=prod --context imageRef=<sha>
```

`env`(`prod` | `stage`)と `imageRef`(ECR の SHA タグ、または `sha256:` 始まりのダイジェスト)は
どちらも必須で、省くとエラーになる。

## CI/CD から動く流れ

普段はここに書いたコマンドを手で打たない。`.github/workflows/` が自動で回す:

1. feature ブランチで PR を作る → 必須チェック(ビルドとテスト / インフラのビルドとテスト /
   イメージをビルドする(push しない)/ 差分レビュー)が通ってから squash マージ(auto-merge 可)
2. `main` への push → `deploy-stage.yml` がイメージを焼いて SHA タグで stage アカウントの ECR へ push し、
   `RecordShopEcMybatisCdkStackStage` を `--context env=stage --context imageRef=<SHAタグ>` でデプロイ、
   CloudFront 経由の `/actuator/health` でスモークする
3. stage で確認できたコミットへタグ `v*` を push → `promote-prod.yml` が `production` Environment の承認を経て、
   stage の ECR にある同じイメージをダイジェスト指定で prod の ECR へコピーし(ビルドし直さない、
   build once, deploy many)、`RecordShopEcMybatisCdkStack` を `--context env=prod --context imageRef=sha256:...`
   でデプロイしてスモークする

手動で `npx cdk deploy` / `npx cdk diff` を流すのは、初期構築の検証や、インフラ定義(`lib/` 配下)だけを
変えて手元で確認したいときに限る。イメージを先に ECR へ push しておく必要がある点は変わらない。

## destroy の手順

```sh
npx cdk destroy <スタック名> --context env=<env> --context imageRef=<任意の文字列>
```

`imageRef` はスタックの合成に必須なコンテキストなので、destroy 時も何か値を渡す(実在するイメージ参照でなくてよい)。
ECR リポジトリ(`infra/ecr-repository.yaml`)と OIDC ロール(`infra/github-oidc.yaml`)はこのスタックの外の
別スタックで、いずれも `DeletionPolicy: Retain` のため、上記の destroy では消えずに残る(消し方は
[`infra/README.md`](infra/README.md#消し方) を参照)。

## Useful commands

* `npm run build`   type-check the project
* `npm run watch`   watch for changes and type-check
* `npm run test`    perform the jest unit tests
* `npm run synth:stage`   `cdk synth --context env=stage --context imageRef=local` の別名(資格情報不要)
* `npx cdk deploy RecordShopEcMybatisCdkStackStage --context env=stage --context imageRef=<sha>`  deploy the stage stack
* `npx cdk diff RecordShopEcMybatisCdkStack --context env=prod --context imageRef=<sha>`    compare deployed stack with current state
* `npx cdk destroy RecordShopEcMybatisCdkStackStage --context env=stage --context imageRef=<sha>` tear down the stage stack
* `npx cdk synth --context env=stage --context imageRef=<sha>`   emits the synthesized CloudFormation template
