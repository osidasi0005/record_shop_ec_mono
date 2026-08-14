# record-shop-ec-cdk

[record-shop-ec-mybatis](../record-shop-ec-mybatis)をAWS上に稼働させるためのCDK(TypeScript)プロジェクト。

## スタック構成

| スタック | 参照するDockerfile | VPC/RDS/ECS/ALB/CloudFront |
|---|---|---|
| `RecordShopEcMybatisCdkStack` | `../record-shop-ec-mybatis` | 専用の1セット |

VPC + NATゲートウェイ1個 + RDS PostgreSQL db.t4g.micro + ECS Fargate 0.25vCPU/0.5GB x1台 + ALB + CloudFront。

**コスト**: 月$60〜90程度(NAT + RDS + ALB + CloudFront)かかり続けるため、使わなくなったら
`npx cdk destroy RecordShopEcMybatisCdkStack` で削除すること。

## Useful commands

* `npm run build`   type-check the project
* `npm run watch`   watch for changes and type-check
* `npm run test`    perform the jest unit tests
* `npx cdk deploy RecordShopEcMybatisCdkStack`  deploy the stack
* `npx cdk diff RecordShopEcMybatisCdkStack`    compare deployed stack with current state
* `npx cdk destroy RecordShopEcMybatisCdkStack` tear down the stack
* `npx cdk synth`   emits the synthesized CloudFormation template
