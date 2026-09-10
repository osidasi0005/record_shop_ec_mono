# phase-5: AWSインフラ(record-shop-ec-cdk)

> このプロンプトを投げる前に、同じフォルダの `00-common.md` を読んで従うこと。
> 作業ディレクトリ: `<親フォルダ>/record-shop-ec-cdk`(空フォルダを新規作成して開く。**兄弟に `record-shop-ec-mybatis` があること**)

## 1. このフェーズの目的

`record-shop-ec-mybatis` を AWS 上で動かす CDK(TypeScript)プロジェクトを作る。
VPC + RDS(PostgreSQL 16)+ ECS Fargate + ALB + CloudFront + Secrets Manager + SES を **1スタック**にまとめ、
`cdk deploy` の出力 `ServiceUrl`(CloudFront の HTTPS URL)でサイトが開くようにする。

## 2. 前提

- phase-4 完了(`../record-shop-ec-mybatis/Dockerfile` が存在しビルドできる)
- AWS CLI 認証済み(`aws sts get-caller-identity`)、Docker Desktop 起動中(`fromAsset` がローカルでイメージをビルドして ECR へ push する)
- `{MAIL_FROM_ADDRESS}` を SES(`{AWS_REGION}`)で **送信元として検証済み**(SES コンソール → Verified identities → メールアドレスを追加し、届いたメールのリンクをクリック)。デプロイ後でもよいが、会員登録機能を試すまでに済ませる
- **コスト**: 月 $60〜90 程度(NAT Gateway + RDS + ALB + CloudFront)が稼働中ずっとかかる。使わない期間は必ず `cdk destroy`

## 3. 仕様

### 3.1 プロジェクト構成

`cdk init app --language typescript` 相当の骨格をベースにする。Git 管理ファイル:

```
.gitignore            *.js / !jest.config.js / *.d.ts / node_modules / .cdk.staging / cdk.out
.npmignore            *.ts / !*.d.ts / .cdk.staging / cdk.out
README.md
bin/record-shop-ec-cdk.ts
cdk.json
cdk.context.json      初回 synth 時に自動生成される(AZ一覧)。コミットしてよい
jest.config.js
lib/record-shop-ec-mybatis-cdk-stack.ts
package.json / package-lock.json
test/record-shop-ec-mybatis-cdk.test.ts
tsconfig.json
```

`package.json`:
```json
{
  "name": "record-shop-ec-cdk",
  "version": "0.1.0",
  "scripts": { "build": "tsc", "watch": "tsc -w", "test": "jest", "cdk": "cdk" },
  "devDependencies": {
    "@swc/core": "^1.15", "@swc/jest": "^0.2", "@types/jest": "^30", "@types/node": "^24",
    "jest": "^30", "aws-cdk": "2.x(最新に固定)", "tsx": "^4", "typescript": "~5.x または ~7.x"
  },
  "dependencies": { "aws-cdk-lib": "^2.2xx", "constructs": "^10" }
}
```
`cdk.json` の `app`: `"npx tsc && npx tsx bin/record-shop-ec-cdk.ts"`(型チェック後に tsx で実行)。`context` は `cdk init` が生成するフィーチャーフラグ一式をそのまま使う。
`tsconfig.json`: `target ES2022`, `module NodeNext`, `moduleResolution NodeNext`, `strict true`, `noEmit true`, `isolatedModules true`, `types ["jest","node"]`, `exclude ["node_modules","cdk.out"]`。
`jest.config.js`: `testEnvironment node`, `roots ['<rootDir>/test']`, `testMatch ['**/*.test.ts']`, `transform {'^.+\\.tsx?$': ['@swc/jest']}`, `setupFilesAfterEnv ['aws-cdk-lib/testhelpers/jest-autoclean']`。

### 3.2 `bin/record-shop-ec-cdk.ts`

```ts
#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib/core';
import { RecordShopEcMybatisCdkStack } from '../lib/record-shop-ec-mybatis-cdk-stack';

const app = new cdk.App();
const env = { account: process.env.CDK_DEFAULT_ACCOUNT, region: process.env.CDK_DEFAULT_REGION };
new RecordShopEcMybatisCdkStack(app, 'RecordShopEcMybatisCdkStack', { env });
```

### 3.3 `lib/record-shop-ec-mybatis-cdk-stack.ts`

`export class RecordShopEcMybatisCdkStack extends cdk.Stack`。クラス冒頭の JSDoc に設計意図(1スタック構成、コスト最小化、`cdk destroy` の案内)を書く。
定数 `const mailFromAddress = '{MAIL_FROM_ADDRESS}';`(コメント: SES で送信検証済みである必要。独自ドメインがあれば `ses.Identity.domain()` + Route53 DKIM が本番向きだが、ホストゾーンが無いのでメールアドレス単位の検証を採用)。

| # | コンストラクトID | 型 | プロパティ |
|---|---|---|---|
| 1 | `RecordShopMybatisVpc` | `ec2.Vpc` | `maxAzs: 2`, `natGateways: 1` |
| 2 | `RecordShopMybatisDatabase` | `rds.DatabaseInstance` | `engine: postgres VER_16`, `instanceType: T4G.MICRO`, `vpc`, `vpcSubnets: PRIVATE_WITH_EGRESS`, `databaseName: 'recordshop_mybatis'`, `credentials: rds.Credentials.fromGeneratedSecret('recordshop')`, `allocatedStorage: 20`, `storageEncrypted: true`, `removalPolicy: DESTROY`, `deletionProtection: false`, `publiclyAccessible: false` |
| 3 | `MybatisAdminPassword` | `secretsmanager.Secret` | `generateSecretString: { secretStringTemplate: '{}', generateStringKey: 'password', excludePunctuation: true, passwordLength: 20 }` |
| 4 | `RecordShopMybatisCluster` | `ecs.Cluster` | `{ vpc }` |
| 5 | `RecordShopMybatisSesIdentity` | `ses.EmailIdentity` | `identity: ses.Identity.email(mailFromAddress)` |
| 6 | `RecordShopMybatisService` | `ecs_patterns.ApplicationLoadBalancedFargateService` | `cluster`, `cpu: 256`, `memoryLimitMiB: 512`, `desiredCount: 1`, `publicLoadBalancer: true`, `healthCheckGracePeriod: 300秒`, `taskImageOptions: { image: ecs.ContainerImage.fromAsset(path.join(__dirname, '../../record-shop-ec-mybatis')), containerPort: 8080, environment: {...}, secrets: {...} }` |
| 7 | `MybatisForwardedProtoFunction` | `cloudfront.Function` | インラインコード(下記) |
| 8 | `RecordShopMybatisDistribution` | `cloudfront.Distribution` | 下記 |
| 9 | `ServiceUrl` / `AlbDirectUrl` | `cdk.CfnOutput` | 下記 |

コンストラクトID に `Mybatis` が入るのは元プロジェクトが JPA 版スタックと併存していた名残。そのまま踏襲してよい(論理IDを後から変えると RDS 等が再作成されるため、最初に決めたら変えない)。

コンテナへ渡す環境変数:

| 種別 | 変数 | 値 |
|---|---|---|
| environment | `DB_HOST` | `database.instanceEndpoint.hostname` |
| environment | `DB_PORT` | `database.instanceEndpoint.port.toString()` |
| environment | `DB_NAME` | `recordshop_mybatis` |
| environment | `ADMIN_EMAIL` | `admin@example.com` |
| environment | `MAIL_FROM_ADDRESS` | `mailFromAddress` |
| environment | `AWS_SES_REGION` | `this.region` |
| secrets | `DB_USERNAME` | `ecs.Secret.fromSecretsManager(database.secret!, 'username')` |
| secrets | `DB_PASSWORD` | `ecs.Secret.fromSecretsManager(database.secret!, 'password')` |
| secrets | `ADMIN_PASSWORD` | `ecs.Secret.fromSecretsManager(adminSecret, 'password')` |

追加設定:
```ts
// SES送信権限。サンドボックス中は宛先Identityにも権限チェックが走るため identity/* で許可
service.taskDefinition.taskRole.addToPrincipalPolicy(new iam.PolicyStatement({
  actions: ['ses:SendEmail', 'ses:SendRawEmail'],
  resources: [`arn:aws:ses:${this.region}:${this.account}:identity/*`],
}));
service.targetGroup.configureHealthCheck({
  path: '/actuator/health', healthyHttpCodes: '200',
  interval: cdk.Duration.seconds(30), timeout: cdk.Duration.seconds(5), healthyThresholdCount: 2,
});
database.connections.allowDefaultPortFrom(service.service, 'Allow inbound from Fargate service');
```

CloudFront Function(VIEWER_REQUEST):
```js
function handler(event) {
    var request = event.request;
    request.headers['x-forwarded-proto-cf'] = { value: 'https' };
    return request;
}
```

Distribution `defaultBehavior`:
```ts
origin: new origins.LoadBalancerV2Origin(service.loadBalancer, { protocolPolicy: cloudfront.OriginProtocolPolicy.HTTP_ONLY }),
viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
cachePolicy: cloudfront.CachePolicy.CACHING_DISABLED,
originRequestPolicy: cloudfront.OriginRequestPolicy.ALL_VIEWER,
functionAssociations: [{ function: forwardedProtoFunction, eventType: cloudfront.FunctionEventType.VIEWER_REQUEST }],
```

Outputs:
- `ServiceUrl` = `https://${distribution.distributionDomainName}`(description: `CloudFront経由のHTTPS URL(こちらを正式なアクセスURLとする)`)
- `AlbDirectUrl` = `http://${service.loadBalancer.loadBalancerDnsName}`(description: `ALB直接アクセス用URL(HTTP、デバッグ用途)`)

### 3.4 テスト `test/record-shop-ec-mybatis-cdk.test.ts`(5件)

`Template.fromStack(new RecordShopEcMybatisCdkStack(app, 'TestStack', { env: { account: '123456789012', region: 'ap-northeast-1' } }))`

| テスト名 | アサーション |
|---|---|
| `RDS(PostgreSQL)がプライベートサブネットに1台作られる` | `AWS::RDS::DBInstance` に `Engine: 'postgres'`, `DBInstanceClass: 'db.t4g.micro'`, `PubliclyAccessible: false` |
| `ECS FargateサービスがALB経由で公開される` | `AWS::ECS::Service` ×1、`AWS::ElasticLoadBalancingV2::LoadBalancer` ×1 かつ `Scheme: 'internet-facing'` |
| `VPCにNATゲートウェイが1個だけ作られる(コスト最小化)` | `AWS::EC2::NatGateway` ×1 |
| `CloudFrontがALBの前段に1つ作られ、HTTPSへリダイレクトする` | `AWS::CloudFront::Distribution` ×1、`DefaultCacheBehavior.ViewerProtocolPolicy: 'redirect-to-https'` |
| `管理者パスワード用のSecrets Managerシークレットが作られる` | `AWS::SecretsManager::Secret` ×2(RDS用 + Admin用) |

`fromAsset` を含むため、テスト実行時も `../../record-shop-ec-mybatis` の Dockerfile が必要(ビルドはしない)。

### 3.5 README.md

- タイトル、`../record-shop-ec-mybatis` を AWS で動かす CDK プロジェクトである旨
- スタック構成表(`RecordShopEcMybatisCdkStack` / 参照 Dockerfile `../record-shop-ec-mybatis` / VPC+NAT×1+RDS t4g.micro+Fargate 0.25vCPU/0.5GB×1+ALB+CloudFront)
- **コスト**: 月$60〜90、不要時 `npx cdk destroy RecordShopEcMybatisCdkStack`
- Useful commands: `npm run build` / `npm run watch` / `npm run test` / `npx cdk deploy RecordShopEcMybatisCdkStack` / `npx cdk diff ...` / `npx cdk destroy ...` / `npx cdk synth`

### 3.6 デプロイ手順(README にも要約を書く)

```bash
npm ci
npm test
npx cdk bootstrap                       # アカウント・リージョンで初回のみ
npx cdk deploy RecordShopEcMybatisCdkStack   # 10〜15分。Dockerビルド+ECR push+CFn
# 出力の ServiceUrl をブラウザで開く(https://xxxx.cloudfront.net/catalog)
aws secretsmanager list-secrets --query "SecretList[?starts_with(Name,'MybatisAdminPassword')].Name" --output text
aws secretsmanager get-secret-value --secret-id <上記の名前> --query SecretString --output text   # 管理者パスワード
npx cdk destroy RecordShopEcMybatisCdkStack  # 使い終わったら必ず
```

### 3.7 GitHub 公開

```bash
gh repo create {GITHUB_OWNER}/record-shop-ec-cdk --private --source=. --remote=origin --push
```

## 4. 設計上の注意・落とし穴(元プロジェクトで実際に起きたこと)

- **クラッシュループ**: 0.25vCPU の Fargate では Spring Boot 起動に 90 秒前後かかり、`healthCheckGracePeriod` 既定 60 秒だと起動直前にヘルスチェック失敗 → タスク強制終了 → 再起動を繰り返した。`healthCheckGracePeriod: 300` と `healthyThresholdCount: 2`(AWS最小値。既定5だと猶予期間を圧迫)で解消
- **HTTPS リダイレクト問題**: CloudFront〜ALB 間は HTTP(独自ドメイン・ACM証明書なし)。ALB は自身への接続プロトコルで `X-Forwarded-Proto` を毎回上書きするため、アプリが HTTPS を認識できずログイン後の Location が `http://` になった。CloudFront Function でカスタムヘッダー `x-forwarded-proto-cf: https` を付け、アプリ側 `CloudFrontProtoFilter` がそれを見て scheme/Location を組み立てる
- **CloudFront のキャッシュ**: セッションCookie・CSRFトークンを含む動的画面なので `CACHING_DISABLED` + `ALL_VIEWER`
- **SES サンドボックス**: 送信元だけでなく宛先の Identity にも `ses:SendEmail` の権限チェックが走るため IAM は `identity/*`。宛先も検証済みメールでないと `MessageRejected` になる(アプリ側は500にせず「確認コードのメールを送信できませんでした…」を表示する)
- **DB は起動のたびに初期化される**(`schema.sql` DROP→CREATE)。ECS のタスク入れ替えでデータもセッション(カート・ログイン)も消える。デモ用途と割り切る
- `cdk.context.json` に AWS アカウントIDが入る。private リポジトリなのでコミットしてよいが、公開する場合は除外する

## 5. 完了条件

- `npm test` 5件 green、`npx cdk synth` 成功
- (デプロイする場合)`ServiceUrl` の `/catalog` が HTTPS で開き、`http://` でアクセスすると `https://` に 301、`/login` → 管理者ログイン(Secrets Manager のパスワード)→ `/admin/releases` が開く。ログイン後リダイレクトの Location が `https://` であること
- `AlbDirectUrl` の `/actuator/health` が 200
- push 完了

## 6. コミット

- `Initial commit: CDKプロジェクト骨格`
- `MyBatis版アプリ用のAWSスタック(VPC/RDS/Fargate/ALB/CloudFront/Secrets/SES)を追加`
