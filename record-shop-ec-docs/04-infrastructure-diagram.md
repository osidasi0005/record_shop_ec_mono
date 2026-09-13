# インフラ構成図

`record-shop-ec-cdk`(AWS CDK、TypeScript)で定義されているAWS構成。`RecordShopEcMybatisCdkStack`
1スタックが、VPC・RDS・ECS Fargate・ALB・CloudFrontを1セット持つ。

## 全体構成

```mermaid
graph TB
    Browser["ブラウザ"]

    subgraph AWS["AWS(ap-northeast-1)"]
        CF["CloudFront Distribution<br/>*.cloudfront.net(標準HTTPS対応)<br/>ViewerProtocolPolicy: REDIRECT_TO_HTTPS"]
        CFFunc["CloudFront Function<br/>(viewer-request)<br/>x-forwarded-proto-cfヘッダー付与"]

        subgraph VPC["VPC(2 AZ)"]
            ALB["Application Load Balancer<br/>internet-facing、HTTPのみ<br/>health check: /actuator/health"]

            subgraph Public["Public Subnet"]
                NAT["NAT Gateway ×1"]
            end

            subgraph Private["Private Subnet(egress)"]
                Fargate["ECS Fargateタスク ×1<br/>0.25vCPU / 0.5GB<br/>Spring Boot"]
                RDS[("RDS PostgreSQL 16<br/>db.t4g.micro<br/>publiclyAccessible: false")]
            end
        end

        SecretsAdmin["Secrets Manager<br/>AdminPassword"]
        SecretsRDS["Secrets Manager<br/>RDS認証情報(自動生成)"]
        ECR["ECR<br/>Dockerイメージ<br/>(このスタックの外。infra/ecr-repository.yamlで作成)"]
        CWLogs["CloudWatch Logs"]
    end

    Browser -->|HTTPS| CF
    CF --> CFFunc
    CF -->|"HTTP(オリジンはHTTP_ONLY)"| ALB
    ALB --> Fargate
    Fargate --> RDS
    Fargate -.->|"起動時にpull"| ECR
    Fargate -.->|ログ出力| CWLogs
    Fargate -.->|"環境変数として注入<br/>(ADMIN_PASSWORD, DB_USERNAME, DB_PASSWORD)"| SecretsAdmin
    Fargate -.-> SecretsRDS
    Fargate -.->|"アウトバウンド(NAT経由)"| NAT

    classDef edge fill:#4a3b6b,stroke:#8b7bb8,color:#fff
    classDef compute fill:#2d5a3d,stroke:#5cb87a,color:#fff
    classDef data fill:#5a3d2d,stroke:#b8845c,color:#fff
    class CF,CFFunc,ALB edge
    class Fargate,ECR compute
    class RDS,SecretsAdmin,SecretsRDS data
```

CDK上は`bin/record-shop-ec-cdk.ts`がこのスタックを定義しており、コンテキスト`env`(`prod`|`stage`)と
`imageRef`(ECRの既存イメージ参照)を指定して`npx cdk deploy RecordShopEcMybatisCdkStack --context env=prod --context imageRef=<sha256:...>`
のようにデプロイ・削除できる。ただし普段はこれを手で打たず、後述のCI/CDから自動的に反映される。

## リクエストフロー(HTTPS化の仕組み)

CloudFrontはブラウザとの間をHTTPSにする一方、CloudFront〜ALB間は意図的にHTTPのまま
(独自ドメイン・ACM証明書を用意していないため、ALBにHTTPS証明書を発行できない)。
この構成では **ALBが自分への接続プロトコル(常にHTTP)で`X-Forwarded-Proto`ヘッダーを
毎回上書きしてしまう** ため、標準的な`X-Forwarded-Proto`の転送だけではアプリ側が
「ブラウザは実際にはHTTPSでアクセスしている」ことを認識できない(リダイレクトの
Locationヘッダーが`http://`になってしまう不具合が実際に発生した)。

```mermaid
sequenceDiagram
    participant B as ブラウザ
    participant CF as CloudFront
    participant Fn as CloudFront Function
    participant ALB as ALB
    participant App as Spring Boot(Fargate)

    B->>CF: GET /login (HTTPS)
    CF->>Fn: viewer-request
    Fn->>Fn: x-forwarded-proto-cf: https を付与
    CF->>ALB: GET /login (HTTP、カスタムヘッダー付き)
    Note over ALB: ALBは自身への接続がHTTPのため<br/>X-Forwarded-Proto: http を上書き設定<br/>(x-forwarded-proto-cfは素通し)
    ALB->>App: GET /login
    Note over App: CloudFrontProtoFilterが<br/>x-forwarded-proto-cf を見て<br/>sendRedirect()のLocationを<br/>https://で直接組み立てる
    App-->>ALB: 302 Location: https://...
    ALB-->>CF: 302
    CF-->>B: 302 Location: https://...
```

対応コード:
[`record-shop-ec-mybatis-cdk-stack.ts`](../record-shop-ec-cdk/lib/record-shop-ec-mybatis-cdk-stack.ts)
のCloudFront Function定義、
[`CloudFrontProtoFilter.java`](../record-shop-ec-mybatis/src/main/java/com/example/recordshop/infrastructure/web/CloudFrontProtoFilter.java)
(アプリ側の対応フィルタ)。

## CI/CDとマルチアカウント構成

prodとstageは別のAWSアカウント。スタック自体(上の全体構成図)は両アカウントで同一の作りで、
環境ごとに変わるのはスタック名とロール名だけ(`RecordShopEcMybatisCdkStack` / `RecordShopEcMybatisCdkStackStage`、
タスクロール`record-shop-ec-task-<env>` / 実行ロール`record-shop-ec-exec-<env>`)。

イメージ用のECRリポジトリと、GitHub ActionsがAWSをOIDCで触るためのロールは、このCDKスタックの外
(`record-shop-ec-cdk/infra/`配下、別のCloudFormationテンプレート)で両アカウントに1回ずつ作る:

| リソース | 役割 |
|---|---|
| ECRリポジトリ`record-shop-ec-mybatis`(両アカウント) | アプリイメージの保管(イミュータブル、`DeletionPolicy: Retain`、スタックの外)。stage側のリポジトリポリシーで、prodのデプロイロールにpullを許可 |
| デプロイロール`record-shop-ec-github-deploy`(両アカウント) | `staging` / `production`のEnvironmentを信頼し、`cdk deploy`を実行する |
| ECR pushロール`record-shop-ec-github-ecr-push`(stageのみ) | `main`へのpush(`ref:refs/heads/main`)だけを信頼し、ECRへのpushだけを許可(CloudFormation・ECSへの権限は持たない) |

```mermaid
flowchart LR
    PR["PR(featureブランチ)"] -->|"必須チェック4つ通過後<br/>squashマージ"| Main["mainへpush"]
    Main -->|"deploy-stage.yml<br/>ECR pushロール(OIDC)"| StageECR["stage ECR<br/>SHAタグでpush"]
    StageECR -->|"cdk deploy<br/>--context env=stage"| Stage["stage環境<br/>(検証用アカウント)"]
    Stage -->|"/actuator/healthで<br/>スモーク"| StageOK["stage確認OK"]
    StageOK -->|"タグ v* をpush"| Promote["promote-prod.yml起動"]
    Promote -->|"production Environmentの<br/>承認ゲート"| Approve["承認"]
    Approve -->|"stage ECRからダイジェスト指定でpull<br/>→ prod ECRへpush<br/>→ ダイジェスト一致を確認"| ProdECR["prod ECR"]
    ProdECR -->|"cdk deploy<br/>--context env=prod"| Prod["prod環境<br/>(本番用アカウント)"]
    Prod -->|"/actuator/healthで<br/>スモーク"| ProdOK["本番反映完了"]
```

**ロールバック**: ECSのサーキットブレーカー(`circuitBreaker: { rollback: true }`。上の全体構成図の
Fargateサービスに設定済み)により、新しいタスク定義が起動しない・タスクレベルのヘルスチェックを
通らない場合はECS自身が前のタスク定義へ自動的に戻す(この場合`cdk deploy`自体が失敗して止まる)。
一方、スモークテスト(`/actuator/health`)が落ちるケース(タスクは起動しヘルスチェックも通ったが
アプリ層に不具合がある場合)は自動では戻らないため、通知を見て手動で前回の`imageRef`を指定して
`cdk deploy`し直す運用。Blue/Greenデプロイとアラームは未導入。

型の出典: [cicd_playbook](https://github.com/osidasi0005/cicd_playbook)(`docs/pipeline.md`、`docs/decisions.md`)。

## 主要リソースの設定値

| リソース | 設定 | 理由・備考 |
|---|---|---|
| VPC | 2 AZ、NATゲートウェイ1個 | コスト最小化(NATは1個あたり時間課金) |
| ECS デプロイ | ローリング更新 + サーキットブレーカー(`rollback: true`)、`minHealthyPercent: 100` / `maxHealthyPercent: 200`、`deregistrationDelay: 30秒` | 新タスクが起動できない場合はECS自身が前のタスク定義へ自動的に戻す(→上の「CI/CDとマルチアカウント構成」) |
| RDS | PostgreSQL 16、db.t4g.micro、20GB、`publiclyAccessible: false` | 学習用途で`removalPolicy: DESTROY`(destroy時にDBごと削除) |
| ECS Fargate | 0.25vCPU / 0.5GB、`desiredCount: 1` | コスト最小化。`healthCheckGracePeriod: 300秒`(起動に90秒前後かかるため) |
| ALBヘルスチェック | `/actuator/health`、`healthyThresholdCount: 2` | デフォルトの5だと猶予期間を圧迫しクラッシュループを起こしたため調整 |
| CloudFront | `CachePolicy.CACHING_DISABLED`、`OriginRequestPolicy.ALL_VIEWER` | セッションCookie・CSRFトークンを含む動的画面のためキャッシュ不可 |
| Secrets Manager | RDS認証情報(自動生成)、Adminパスワード(自動生成20文字) | 平文パスワードをコード・環境変数に書かない |

これらの設定値は、クラッシュループ等を実際に起こして試行錯誤の末に調整・確定させたもの。

## コスト構造

NAT Gateway + RDS + ALB + CloudFrontが常時課金対象で、**合計おおよそ月$60〜90程度**
(CloudFrontはデモ規模のアクセス量ならほぼ誤差)。使わなくなったら

```bash
cd record-shop-ec-cdk
npx cdk destroy RecordShopEcMybatisCdkStack
```

で削除する運用としている。この方針に従い、**2026-08-16に削除済み**
(記録・復元手順は[07-aws-teardown-record.md](07-aws-teardown-record.md))。
本ドキュメントの構成図は、再デプロイすれば同じものが再現される設計の記述である。
