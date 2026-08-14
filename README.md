# record-shop-ec-cdk

[record-shop-ec-jpa](../record-shop-ec-jpa)(JPA版)と[record-shop-ec-mybatis](../record-shop-ec-mybatis)(MyBatis版)を
AWS上に並行稼働させるためのCDK(TypeScript)プロジェクト。2つのスタックが独立して定義されている。

## スタック構成

| スタック | 参照するDockerfile | VPC/RDS/ECS/ALB/CloudFront |
|---|---|---|
| `RecordShopEcCdkStack` | `../record-shop-ec-jpa` | 専用の1セット |
| `RecordShopEcMybatisCdkStack` | `../record-shop-ec-mybatis` | 専用の1セット(JPA版とは完全に別リソース) |

どちらも同じ構成(VPC + NATゲートウェイ1個 + RDS PostgreSQL db.t4g.micro + ECS Fargate 0.25vCPU/0.5GB x1台 +
ALB + CloudFront)。JPA版・MyBatis版のインフラ構成そのものに違いは無く、コンテナ内で動くアプリの
永続化層(JPA vs MyBatis)だけが異なる。

**コスト**: 1スタックあたり月$60〜90程度(NAT + RDS + ALB + CloudFront)。2スタック同時稼働させると
単純に倍(月$120〜180程度)かかり続けるため、比較検証が終わったら使わない方を
`npx cdk destroy <スタック名>` で削除すること。

## Useful commands

* `npm run build`   type-check the project
* `npm run watch`   watch for changes and type-check
* `npm run test`    perform the jest unit tests
* `npx cdk deploy <StackName>`  deploy a specific stack (e.g. `RecordShopEcMybatisCdkStack`)
* `npx cdk diff <StackName>`    compare deployed stack with current state
* `npx cdk destroy <StackName>` tear down a specific stack
* `npx cdk synth`   emits the synthesized CloudFormation template for all stacks
