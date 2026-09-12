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

## Useful commands

* `npm run build`   type-check the project
* `npm run watch`   watch for changes and type-check
* `npm run test`    perform the jest unit tests
* `npm run synth:stage`   `cdk synth --context env=stage --context imageRef=local` の別名(資格情報不要)
* `npx cdk deploy RecordShopEcMybatisCdkStackStage --context env=stage --context imageRef=<sha>`  deploy the stage stack
* `npx cdk diff RecordShopEcMybatisCdkStack --context env=prod --context imageRef=<sha>`    compare deployed stack with current state
* `npx cdk destroy RecordShopEcMybatisCdkStackStage --context env=stage --context imageRef=<sha>` tear down the stage stack
* `npx cdk synth --context env=stage --context imageRef=<sha>`   emits the synthesized CloudFormation template
