import * as cdk from 'aws-cdk-lib/core';
import { Construct } from 'constructs';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecs_patterns from 'aws-cdk-lib/aws-ecs-patterns';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as origins from 'aws-cdk-lib/aws-cloudfront-origins';
import * as ses from 'aws-cdk-lib/aws-ses';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as path from 'path';

/**
 * レコード販売ECサイトのAWSインフラ(MyBatis永続化層版)。
 * VPC + RDS(PostgreSQL) + ECS Fargate + ALB + CloudFront を1スタックにまとめている。
 *
 * コスト最小化のため: NATゲートウェイ1個のみ、Fargate 0.25vCPU/0.5GB x1台、RDS db.t4g.micro。
 * それでも起動しているだけで課金される(NAT + RDS + ALB + CloudFrontで概算 月$60〜90程度、
 * CloudFrontはデモ規模のアクセス量ならほぼ誤差)ため、デモが終わったら
 * `cdk destroy RecordShopEcMybatisCdkStack` で削除すること。
 *
 * リソースの論理ID(`RecordShopMybatis*`)には、かつて併存していたJPA版スタックとの区別のため
 * 命名した名残の"Mybatis"が残っているが、CDKの論理IDを変更するとAWS上の実リソース
 * (RDS等)が再作成されてしまうため、あえてそのまま維持している。
 */
export class RecordShopEcMybatisCdkStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

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
    // TODO: 実際に検証可能なメールアドレス/ドメインに置き換えること。
    const mailFromAddress = 'no-reply@example.com';

    // SESでメールアドレスIDを検証登録する(開発中は受信箱に届く確認メールをクリックする手動検証が必要)。
    // 独自ドメインを持つ場合は ses.Identity.domain('example.com') + Route53 DKIM自動設定が本番向きだが、
    // このスタックにはRoute53ホストゾーンが無いため、初期実装はメールアドレス単位の検証を採用する。
    const sesIdentity = new ses.EmailIdentity(this, 'RecordShopMybatisSesIdentity', {
      identity: ses.Identity.email(mailFromAddress),
    });

    // record-shop-ec-mybatis の Dockerfile からイメージをビルドし、ECRへ自動プッシュする。
    const service = new ecs_patterns.ApplicationLoadBalancedFargateService(this, 'RecordShopMybatisService', {
      cluster,
      cpu: 256,
      memoryLimitMiB: 512,
      desiredCount: 1,
      publicLoadBalancer: true,
      // 0.25vCPUのFargateタスクではSpring Boot起動に90秒前後かかることがあり、
      // デフォルト60秒の猶予期間だと起動完了直前にヘルスチェック失敗でタスクが強制終了→
      // 再起動…を繰り返すクラッシュループに陥ることを実際に発生させて確認した。
      // 猶予期間を300秒まで伸ばし、healthyThresholdCountも下のconfigureHealthCheck側で
      // 2(AWSの最小値)まで下げることで、起動完了後すぐ(60秒)healthy判定されるようにする。
      healthCheckGracePeriod: cdk.Duration.seconds(300),
      taskImageOptions: {
        image: ecs.ContainerImage.fromAsset(path.join(__dirname, '../../record-shop-ec-mybatis')),
        containerPort: 8080,
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
      },
    });

    // ECSタスクロールにSES送信権限を付与(最小権限: 検証済みIdentityのARNに限定)
    service.taskDefinition.taskRole.addToPrincipalPolicy(new iam.PolicyStatement({
      actions: ['ses:SendEmail', 'ses:SendRawEmail'],
      resources: [`arn:aws:ses:${this.region}:${this.account}:identity/${mailFromAddress}`],
    }));

    service.targetGroup.configureHealthCheck({
      path: '/actuator/health',
      healthyHttpCodes: '200',
      interval: cdk.Duration.seconds(30),
      timeout: cdk.Duration.seconds(5),
      healthyThresholdCount: 2,
    });

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
  }
}
