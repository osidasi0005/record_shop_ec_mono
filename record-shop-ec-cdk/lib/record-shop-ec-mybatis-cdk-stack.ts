import * as cdk from 'aws-cdk-lib/core';
import { Construct } from 'constructs';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecs_patterns from 'aws-cdk-lib/aws-ecs-patterns';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as origins from 'aws-cdk-lib/aws-cloudfront-origins';
import * as ses from 'aws-cdk-lib/aws-ses';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as logs from 'aws-cdk-lib/aws-logs';

/** デプロイ先の環境。prod(常時稼働の本番)と stage(検証用)の 2 つのみ。 */
export type DeployEnvironment = 'prod' | 'stage';

/** 値が DeployEnvironment かどうかを判定する(bin / test の両方から使う)。 */
export function isDeployEnvironment(value: unknown): value is DeployEnvironment {
  return value === 'prod' || value === 'stage';
}

/**
 * CDK コンテキスト `env` から環境名を取り出す。
 *
 * **必須。** prod と stage は別 AWS アカウントで、同名リソース(タスクロール名など)が
 * 衝突しないという前提でスタックを組んでいる。指定を省いた既定値(例えば prod)を
 * 持たせると、渡し忘れたコマンドが黙って本番を指してしまうため、
 * 未指定・不正値のときはここでエラーにして止める。
 */
export function resolveDeployEnvironment(app: cdk.App): DeployEnvironment {
  const value = app.node.tryGetContext('env');
  if (!isDeployEnvironment(value)) {
    throw new Error(
      `コンテキスト "env" は "prod" か "stage" のどちらかを指定してください` +
        `(例: --context env=stage)。渡された値: ${JSON.stringify(value)}`,
    );
  }
  return value;
}

/**
 * CDK コンテキスト `imageRef` から、ECR に積む/積んであるイメージの参照を取り出す。
 *
 * **必須。** SHA タグ(例 `0123abcd...`)か、ダイジェスト(`sha256:...`)のどちらかを想定するが、
 * どちらの形式かは `ecs.ContainerImage.fromEcrRepository` が自動判別するため、
 * ここでは「空でない文字列」であることだけを確かめる。
 */
export function resolveImageRef(app: cdk.App): string {
  const value = app.node.tryGetContext('imageRef');
  if (typeof value !== 'string' || value.trim() === '') {
    throw new Error(
      'コンテキスト "imageRef" は必須です。ECR の SHA タグ、または "sha256:" で始まる' +
        'ダイジェストを指定してください(例: --context imageRef=0123abcd... ' +
        'または --context imageRef=sha256:...)。',
    );
  }
  return value.trim();
}

/**
 * 環境ごとに変える値。
 *
 * **構成そのものは揃える。変えてよいのは名前だけ。** VPC / RDS / ECS / ALB / CloudFront の
 * 作りは prod と stage で同一にする。stage を違う作りにすると、stage で通ったことが
 * 本番の保証にならなくなる。ここに 1 か所にまとめておくことで、
 * 「どこが環境で変わるのか」をこのファイルの外から探さずに読めるようにする。
 */
interface EnvironmentSettings {
  /** CloudFormation のスタック名に付ける接尾辞(本番は接尾辞なしで従来の名前を保つ)。 */
  readonly stackNameSuffix: string;
}

const ENVIRONMENT_SETTINGS: Record<DeployEnvironment, EnvironmentSettings> = {
  prod: { stackNameSuffix: '' },
  stage: { stackNameSuffix: 'Stage' },
};

/**
 * 環境に対応するスタック名。
 *
 * 本番は接尾辞を付けない(既存のスタック名を変えると CloudFormation は別スタックとして
 * 扱ってしまい、稼働中の環境が置き去りになるため)。
 */
export function stackNameFor(environmentName: DeployEnvironment): string {
  return `RecordShopEcMybatisCdkStack${ENVIRONMENT_SETTINGS[environmentName].stackNameSuffix}`;
}

/**
 * タスク定義のロールに、自分で付ける名前。
 *
 * **`iam:PassRole` はタグで絞れない**(使える条件キーは `iam:PassedToService` と
 * `iam:AssociatedResourceArn` の 2 つだけで、IAM ロールには CloudFormation の自動タグも
 * 付かない)。OIDC のデプロイロールがこの PassRole を ARN で絞れるように、
 * CloudFormation の採番に任せず名前を自分で決める。環境名を含むので prod と stage で
 * 衝突しない(そもそも別アカウントだが、名前でも重ならないようにする)。
 */
export function taskRoleNameFor(environmentName: DeployEnvironment): string {
  return `record-shop-ec-task-${environmentName}`;
}

/** タスクの実行ロール(ECR から引く・ログを書く)の名前。→ {@link taskRoleNameFor} */
export function executionRoleNameFor(environmentName: DeployEnvironment): string {
  return `record-shop-ec-exec-${environmentName}`;
}

/**
 * スタック全体に付けるタグのキー。値はスタック名そのもの。
 *
 * IAM ロールには CloudFormation の自動タグ(`aws:cloudformation:stack-name` 等)が
 * 付かないため、「このロールがどのスタックのものか」を追えるように自前で付ける。
 */
export const STACK_NAME_TAG = 'record-shop-ec:stack-name';

/** このスタックが参照する ECR リポジトリの名前。インフラの外(infra/ecr-repository.yaml)で作る。 */
const APP_ECR_REPOSITORY_NAME = 'record-shop-ec-mybatis';

export interface RecordShopEcMybatisCdkStackProps extends cdk.StackProps {
  /** デプロイ先の環境。prod と stage で構成は同一(→ {@link EnvironmentSettings})。 */
  readonly environmentName: DeployEnvironment;

  /**
   * ECS タスクに積むアプリ(record-shop-ec-mybatis)イメージの参照。
   * ECR の SHA タグ、または `sha256:` で始まるダイジェスト。
   */
  readonly imageRef: string;
}

/**
 * レコード販売ECサイトのAWSインフラ(MyBatis永続化層版)。
 * VPC + RDS(PostgreSQL) + ECS Fargate + ALB + CloudFront を1スタックにまとめている。
 *
 * コスト最小化のため: NATゲートウェイ1個のみ、Fargate 0.25vCPU/0.5GB x1台、RDS db.t4g.micro。
 * それでも起動しているだけで課金される(NAT + RDS + ALB + CloudFrontで概算 月$60〜90程度、
 * CloudFrontはデモ規模のアクセス量ならほぼ誤差)ため、使わなくなったら
 * `cdk destroy <スタック名>` で削除すること。
 *
 * `environmentName` で `prod` / `stage` の 2 環境を作り分ける(→ {@link stackNameFor})。
 * **構成は同一**で、違うのはスタック名とロール名だけ。
 *
 * リソースの論理ID(`RecordShopMybatis*`)には、かつて併存していたJPA版スタックとの区別のため
 * 命名した名残の"Mybatis"が残っているが、CDKの論理IDを変更するとAWS上の実リソース
 * (RDS等)が再作成されてしまうため、あえてそのまま維持している。
 */
export class RecordShopEcMybatisCdkStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: RecordShopEcMybatisCdkStackProps) {
    super(scope, id, props);

    const { environmentName, imageRef } = props;

    // スタック全体にタグを付ける。IAM ロールには CloudFormation の自動タグが付かないため、
    // 「このロールがどのスタックのものか」を追えるように自前で付ける(→ STACK_NAME_TAG)。
    cdk.Tags.of(this).add(STACK_NAME_TAG, id);

    // account/region を渡さない(env-agnostic)。fromLookup を使わない構成なので、
    // `cdk synth` は AWS 資格情報が無くても通る。VPC は maxAzs で AZ 数を指定するだけで、
    // 実在の AZ 名を引きにいかない。
    const vpc = new ec2.Vpc(this, 'RecordShopMybatisVpc', {
      maxAzs: 2,
      natGateways: 1,
    });

    const database = new rds.DatabaseInstance(this, 'RecordShopMybatisDatabase', {
      engine: rds.DatabaseInstanceEngine.postgres({
        version: rds.PostgresEngineVersion.VER_16,
      }),
      instanceType: ec2.InstanceType.of(ec2.InstanceClass.T4G, ec2.InstanceSize.MICRO),
      vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      databaseName: 'recordshop_mybatis',
      credentials: rds.Credentials.fromGeneratedSecret('recordshop'),
      allocatedStorage: 20,
      storageEncrypted: true,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
      deletionProtection: false,
      publiclyAccessible: false,
    });

    const adminSecret = new secretsmanager.Secret(this, 'MybatisAdminPassword', {
      generateSecretString: {
        secretStringTemplate: JSON.stringify({}),
        generateStringKey: 'password',
        excludePunctuation: true,
        passwordLength: 20,
      },
    });

    const cluster = new ecs.Cluster(this, 'RecordShopMybatisCluster', { vpc });

    // 会員登録の確認コード・登録完了メールの送信元(SESで送信検証済みのメールアドレスである必要がある)。
    const mailFromAddress = 'osidasi0005@gmail.com';

    // SESでメールアドレスIDを検証登録する(開発中は受信箱に届く確認メールをクリックする手動検証が必要)。
    // 独自ドメインを持つ場合は ses.Identity.domain('example.com') + Route53 DKIM自動設定が本番向きだが、
    // このスタックにはRoute53ホストゾーンが無いため、初期実装はメールアドレス単位の検証を採用する。
    // prod / stage は別 AWS アカウントなので、両方で作っても Identity は衝突しない。
    new ses.EmailIdentity(this, 'RecordShopMybatisSesIdentity', {
      identity: ses.Identity.email(mailFromAddress),
    });

    /**
     * ECR の既存リポジトリを参照する(`ContainerImage.fromAsset` はやめた)。
     *
     * このリポジトリはこのスタックの外(`infra/ecr-repository.yaml`)で先に作る。
     * スタック側で作らない理由は 2 つ:
     *   1. スタックを destroy するたびにリポジトリごと消えると、次にデプロイするとき
     *      イメージが 1 枚も無い空のリポジトリから始まり、CI が積み直すまでデプロイできない。
     *   2. デプロイパイプラインは「ビルド→検証(スキャン/スモーク)→デプロイ」の順で
     *      同じ SHA タグを使い回す前提で組む。スタックがリポジトリの寿命を握っていると、
     *      再作成のたびにリポジトリが変わってしまい、検証済みの SHA と同じタグで
     *      別の中身(別リポジトリの同名タグ)を指してしまう余地が生まれる。
     */
    const repository = ecr.Repository.fromRepositoryName(
      this,
      'AppRepository',
      APP_ECR_REPOSITORY_NAME,
    );

    // **ロール名は自分で決める。** OIDC のデプロイロールは、この名前の ARN にだけ
    // PassRole を許す形で絞る想定(→ taskRoleNameFor / executionRoleNameFor)。
    // 説明文は ASCII のみ(日本語を入れると CloudFormation が実行時に弾く)。
    const taskRole = new iam.Role(this, 'RecordShopMybatisTaskRole', {
      roleName: taskRoleNameFor(environmentName),
      assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
      description: 'Task role for the record-shop-ec mybatis service.',
    });
    const executionRole = new iam.Role(this, 'RecordShopMybatisExecutionRole', {
      roleName: executionRoleNameFor(environmentName),
      assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
      description: 'Execution role for the record-shop-ec mybatis service.',
    });

    const taskDefinition = new ecs.FargateTaskDefinition(this, 'RecordShopMybatisTaskDef', {
      taskRole,
      executionRole,
      cpu: 256,
      memoryLimitMiB: 512,
    });

    taskDefinition.addContainer('web', {
      // ECR の既存イメージを参照する。sha256: 始まりはダイジェスト、それ以外はタグとして
      // `fromEcrRepository` が自動判別し、実行ロールへの pull 権限も同時に付与する。
      image: ecs.ContainerImage.fromEcrRepository(repository, imageRef),
      portMappings: [{ containerPort: 8080 }],
      // タスク定義を自作にすると、ecs_patterns が既定で付けていた awslogs が付かなくなる。
      // 失敗の原因は CloudWatch Logs にしか出ないので、明示的に付ける(保持は 1 週間。学習用途)。
      logging: ecs.LogDrivers.awsLogs({
        streamPrefix: 'web',
        logRetention: logs.RetentionDays.ONE_WEEK,
      }),
      environment: {
        DB_HOST: database.instanceEndpoint.hostname,
        DB_PORT: database.instanceEndpoint.port.toString(),
        DB_NAME: 'recordshop_mybatis',
        ADMIN_EMAIL: 'admin@example.com',
        MAIL_FROM_ADDRESS: mailFromAddress,
        AWS_SES_REGION: this.region,
      },
      secrets: {
        DB_USERNAME: ecs.Secret.fromSecretsManager(database.secret!, 'username'),
        DB_PASSWORD: ecs.Secret.fromSecretsManager(database.secret!, 'password'),
        ADMIN_PASSWORD: ecs.Secret.fromSecretsManager(adminSecret, 'password'),
      },
    });

    const service = new ecs_patterns.ApplicationLoadBalancedFargateService(
      this,
      'RecordShopMybatisService',
      {
        cluster,
        taskDefinition,
        desiredCount: 1,
        publicLoadBalancer: true,
        // 新しいタスクが起動しない/ヘルスチェックを通らないとき、既定では CloudFormation が
        // 長時間待ち続ける。壊れていると分かった時点で前のタスク定義へ自動的に巻き戻す。
        circuitBreaker: { rollback: true },
        // 既定の minHealthyPercent は 50%。タスクが 1 本なので floor(1 × 0.5) = 0 となり、
        // ECS は新しいタスクを立てる前に古いタスクを止めてしまう(リリースのたびに
        // Spring Boot の起動時間がまるごとダウンタイムになる)。100% にして
        // 新しいタスクが healthy になってから古いタスクを止めるようにする。
        // 入れ替え中だけ 2 本になるので、その間だけ料金も 2 倍になる(maxHealthyPercent: 200)。
        minHealthyPercent: 100,
        maxHealthyPercent: 200,
        // 0.25vCPUのFargateタスクではSpring Boot起動に90秒前後かかることがあり、
        // デフォルト60秒の猶予期間だと起動完了直前にヘルスチェック失敗でタスクが強制終了→
        // 再起動…を繰り返すクラッシュループに陥ることを実際に発生させて確認した。
        // 猶予期間を300秒まで伸ばし、healthyThresholdCountも下のconfigureHealthCheck側で
        // 2(AWSの最小値)まで下げることで、起動完了後すぐ(60秒)healthy判定されるようにする。
        healthCheckGracePeriod: cdk.Duration.seconds(300),
      },
    );

    // ECSタスクロールにSES送信権限を付与。
    // SESサンドボックスモード中は送信元だけでなく宛先(To)のIdentityに対しても
    // IAM側でses:SendEmailの権限チェックが行われるため、送信元Identityのみへの限定はできない。
    // このAWSアカウント内のSES Identity全体(=このAWSアカウント自身が検証したメールアドレス/ドメインのみ)
    // への送信を許可する(本番アクセス取得後は宛先の検証・権限制約自体が不要になる)。
    taskRole.addToPrincipalPolicy(
      new iam.PolicyStatement({
        actions: ['ses:SendEmail', 'ses:SendRawEmail'],
        resources: [`arn:aws:ses:${this.region}:${this.account}:identity/*`],
      }),
    );

    service.targetGroup.configureHealthCheck({
      path: '/actuator/health',
      healthyHttpCodes: '200',
      interval: cdk.Duration.seconds(30),
      timeout: cdk.Duration.seconds(5),
      healthyThresholdCount: 2,
    });

    // 既定の deregistrationDelay は 300 秒。このアプリは処理をリクエスト内で完結させており、
    // 30 秒あれば処理中のリクエストは返しきるため、入れ替えのたびの待ち時間を短くする。
    service.targetGroup.setAttribute('deregistration_delay.timeout_seconds', '30');

    database.connections.allowDefaultPortFrom(service.service, 'Allow inbound from Fargate service');

    const forwardedProtoFunction = new cloudfront.Function(this, 'MybatisForwardedProtoFunction', {
      code: cloudfront.FunctionCode.fromInline(`
function handler(event) {
    var request = event.request;
    request.headers['x-forwarded-proto-cf'] = { value: 'https' };
    return request;
}
`),
    });

    const distribution = new cloudfront.Distribution(this, 'RecordShopMybatisDistribution', {
      defaultBehavior: {
        origin: new origins.LoadBalancerV2Origin(service.loadBalancer, {
          protocolPolicy: cloudfront.OriginProtocolPolicy.HTTP_ONLY,
        }),
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
        cachePolicy: cloudfront.CachePolicy.CACHING_DISABLED,
        originRequestPolicy: cloudfront.OriginRequestPolicy.ALL_VIEWER,
        functionAssociations: [{
          function: forwardedProtoFunction,
          eventType: cloudfront.FunctionEventType.VIEWER_REQUEST,
        }],
      },
    });

    new cdk.CfnOutput(this, 'ServiceUrl', {
      value: `https://${distribution.distributionDomainName}`,
      description: 'CloudFront経由のHTTPS URL(こちらを正式なアクセスURLとする)',
    });

    new cdk.CfnOutput(this, 'AlbDirectUrl', {
      value: `http://${service.loadBalancer.loadBalancerDnsName}`,
      description: 'ALB直接アクセス用URL(HTTP、デバッグ用途)',
    });

    // スモークテストとロールバック確認に使う(→ このスタックの完了条件)。
    new cdk.CfnOutput(this, 'AppImageRef', {
      value: imageRef,
      description: 'このデプロイで反映したアプリイメージの参照(SHAタグまたはダイジェスト)',
    });

    new cdk.CfnOutput(this, 'ClusterName', {
      value: cluster.clusterName,
      description: 'ECSクラスター名',
    });

    new cdk.CfnOutput(this, 'ServiceName', {
      value: service.service.serviceName,
      description: 'ECSサービス名',
    });
  }
}
