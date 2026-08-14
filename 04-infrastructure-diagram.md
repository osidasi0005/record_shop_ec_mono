# インフラ構成図

`record-shop-ec-cdk`(AWS CDK、TypeScript)で定義されているAWS構成。**2つの独立したスタック**
(`RecordShopEcCdkStack`=JPA版、`RecordShopEcMybatisCdkStack`=MyBatis版)を持ち、それぞれが
VPC・RDS・ECS Fargate・ALB・CloudFrontをまるごと1セット持つ(リソースの共有は一切無い)。
参照するDockerイメージのビルド元(`record-shop-ec-domain`か`record-shop-ec-mybatis`か)以外、
2つのスタックの構成は完全に同一。

**現在の稼働状況**: JPA版スタック(`RecordShopEcCdkStack`)はコスト最小化のため`cdk destroy`
で削除済み。MyBatis版スタック(`RecordShopEcMybatisCdkStack`)のみ稼働中(下記コスト構造を参照)。

## 全体構成(スタック1セットあたり)

```mermaid
graph TB
    Browser["ブラウザ"]

    subgraph AWS["AWS(ap-northeast-1)― JPA版・MyBatis版それぞれ1セット"]
        CF["CloudFront Distribution<br/>*.cloudfront.net(標準HTTPS対応)<br/>ViewerProtocolPolicy: REDIRECT_TO_HTTPS"]
        CFFunc["CloudFront Function<br/>(viewer-request)<br/>x-forwarded-proto-cfヘッダー付与"]

        subgraph VPC["VPC(2 AZ)"]
            ALB["Application Load Balancer<br/>internet-facing、HTTPのみ<br/>health check: /actuator/health"]

            subgraph Public["Public Subnet"]
                NAT["NAT Gateway ×1"]
            end

            subgraph Private["Private Subnet(egress)"]
                Fargate["ECS Fargateタスク ×1<br/>0.25vCPU / 0.5GB<br/>Spring Boot(JPA版 or MyBatis版)"]
                RDS[("RDS PostgreSQL 16<br/>db.t4g.micro<br/>publiclyAccessible: false")]
            end
        end

        SecretsAdmin["Secrets Manager<br/>AdminPassword"]
        SecretsRDS["Secrets Manager<br/>RDS認証情報(自動生成)"]
        ECR["ECR<br/>Dockerイメージ"]
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

この1セットが**JPA版・MyBatis版それぞれ独立に**存在する(VPCもRDSもALBもCloudFrontも
共有しない)。CDK上は`bin/record-shop-ec-cdk.ts`が両スタックを定義しており、
`npx cdk deploy <スタック名>` / `npx cdk destroy <スタック名>` で個別にデプロイ・削除できる。

## リクエストフロー(HTTPS化の仕組み)

CloudFrontはブラウザとの間をHTTPSにする一方、CloudFront〜ALB間は意図的にHTTPのまま
(独自ドメイン・ACM証明書を用意していないため、ALBにHTTPS証明書を発行できない)。
この構成では **ALBが自分への接続プロトコル(常にHTTP)で`X-Forwarded-Proto`ヘッダーを
毎回上書きしてしまう** ため、標準的な`X-Forwarded-Proto`の転送だけではアプリ側が
「ブラウザは実際にはHTTPSでアクセスしている」ことを認識できない(リダイレクトの
Locationヘッダーが`http://`になってしまう不具合が実際に発生した)。この仕組みはJPA版・
MyBatis版で完全に同一(`CloudFrontProtoFilter`はWeb層の一部としてどちらにも無修正コピーされている)。

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
[`record-shop-ec-cdk-stack.ts`](../record-shop-ec-cdk/lib/record-shop-ec-cdk-stack.ts)(JPA版)/
[`record-shop-ec-mybatis-cdk-stack.ts`](../record-shop-ec-cdk/lib/record-shop-ec-mybatis-cdk-stack.ts)(MyBatis版)
のCloudFront Function定義(内容は同一)、
[`CloudFrontProtoFilter.java`](../record-shop-ec-domain/src/main/java/com/example/recordshop/infrastructure/web/CloudFrontProtoFilter.java)
(アプリ側の対応フィルタ、両バージョンに無修正コピー)。

## 主要リソースの設定値(JPA版・MyBatis版共通)

| リソース | 設定 | 理由・備考 |
|---|---|---|
| VPC | 2 AZ、NATゲートウェイ1個 | コスト最小化(NATは1個あたり時間課金) |
| RDS | PostgreSQL 16、db.t4g.micro、20GB、`publiclyAccessible: false` | 学習用途で`removalPolicy: DESTROY`(destroy時にDBごと削除) |
| ECS Fargate | 0.25vCPU / 0.5GB、`desiredCount: 1` | コスト最小化。`healthCheckGracePeriod: 300秒`(起動に90秒前後かかるため) |
| ALBヘルスチェック | `/actuator/health`、`healthyThresholdCount: 2` | デフォルトの5だと猶予期間を圧迫しクラッシュループを起こしたため調整 |
| CloudFront | `CachePolicy.CACHING_DISABLED`、`OriginRequestPolicy.ALL_VIEWER` | セッションCookie・CSRFトークンを含む動的画面のためキャッシュ不可 |
| Secrets Manager | RDS認証情報(自動生成)、Adminパスワード(自動生成20文字) | 平文パスワードをコード・環境変数に書かない |

これらの設定値はJPA版で試行錯誤して確定させたもの(クラッシュループ等を実際に起こして
調整した経緯は[record-shop-ec-domain](../record-shop-ec-domain)のREADME参照)を、MyBatis版
スタックにもそのまま踏襲している。

## コスト構造

1スタックあたりNAT Gateway + RDS + ALB + CloudFrontが常時課金対象で、**合計おおよそ月$60〜90程度**
(CloudFrontはデモ規模のアクセス量ならほぼ誤差)。2スタック同時稼働させると単純に倍(月$120〜180程度)
になるため、比較検証が終わったスタックから順に

```bash
cd record-shop-ec-cdk
npx cdk destroy RecordShopEcCdkStack        # JPA版
npx cdk destroy RecordShopEcMybatisCdkStack # MyBatis版
```

で削除する運用としている。現状はJPA版を削除済み・MyBatis版のみ稼働中のため、課金は
月$60〜90程度に縮小している。
