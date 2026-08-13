import * as cdk from 'aws-cdk-lib/core';
import { Construct } from 'constructs';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecs_patterns from 'aws-cdk-lib/aws-ecs-patterns';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as path from 'path';

/**
 * レコード販売ECサイトの「歩く骨格」フェーズ用の最小AWSインフラ。
 * VPC + RDS(PostgreSQL) + ECS Fargate + ALB を1スタックにまとめている。
 *
 * コスト最小化のため: NATゲートウェイ1個のみ、Fargate 0.25vCPU/0.5GB x1台、RDS db.t4g.micro。
 * それでも起動しているだけで課金される(NAT + RDS + ALBで概算 月$60〜90程度)ため、
 * デモが終わったら `cdk destroy` で削除すること。
 */
export class RecordShopEcCdkStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const vpc = new ec2.Vpc(this, 'RecordShopVpc', {
      maxAzs: 2,
      natGateways: 1,
    });

    const database = new rds.DatabaseInstance(this, 'RecordShopDatabase', {
      engine: rds.DatabaseInstanceEngine.postgres({
        version: rds.PostgresEngineVersion.VER_16,
      }),
      instanceType: ec2.InstanceType.of(ec2.InstanceClass.T4G, ec2.InstanceSize.MICRO),
      vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      databaseName: 'recordshop',
      credentials: rds.Credentials.fromGeneratedSecret('recordshop'),
      allocatedStorage: 20,
      storageEncrypted: true,
      // 学習・デモ用途のため destroy 時にDBごと削除する(本番運用ではSNAPSHOT等に変更する)
      removalPolicy: cdk.RemovalPolicy.DESTROY,
      deletionProtection: false,
      publiclyAccessible: false,
    });

    const cluster = new ecs.Cluster(this, 'RecordShopCluster', { vpc });

    // record-shop-ec-domain の Dockerfile からイメージをビルドし、ECRへ自動プッシュする。
    const service = new ecs_patterns.ApplicationLoadBalancedFargateService(this, 'RecordShopService', {
      cluster,
      cpu: 256,
      memoryLimitMiB: 512,
      desiredCount: 1,
      publicLoadBalancer: true,
      // 0.25vCPUのFargateタスクではSpring Boot起動に90秒前後かかることがあり、
      // デフォルト60秒の猶予期間だと起動完了直前にヘルスチェック失敗でタスクが強制終了→
      // 再起動…を繰り返すクラッシュループに陥る(実際に発生させて確認した)。
      // さらにデフォルトの healthyThresholdCount=5(30秒間隔で5回連続成功が必要=150秒)と
      // 合わせると、起動完了(~90秒)から healthy 判定までの合計所要時間が240秒前後になり、
      // 180秒の猶予期間でもギリギリ足りず停止させられるケースを実際に確認した。
      // 猶予期間を300秒まで伸ばし、healthyThresholdCount も下の configureHealthCheck 側で
      // 2(AWSの最小値)まで下げることで、起動完了後すぐ(60秒)healthy判定されるようにする。
      healthCheckGracePeriod: cdk.Duration.seconds(300),
      taskImageOptions: {
        image: ecs.ContainerImage.fromAsset(path.join(__dirname, '../../record-shop-ec-domain')),
        containerPort: 8080,
        environment: {
          DB_HOST: database.instanceEndpoint.hostname,
          DB_PORT: database.instanceEndpoint.port.toString(),
          DB_NAME: 'recordshop',
        },
        secrets: {
          DB_USERNAME: ecs.Secret.fromSecretsManager(database.secret!, 'username'),
          DB_PASSWORD: ecs.Secret.fromSecretsManager(database.secret!, 'password'),
        },
      },
    });

    // ALBのデフォルトヘルスチェックパス("/")には何もマッピングしておらず404になるため、
    // Spring Boot Actuator の /actuator/health を明示的に指定する。
    // (これを直さず deploy すると、タスクがいつまでも healthy にならず ECS::Service の作成が
    //  "Exceeded attempts to wait" で失敗する ―― 実際に一度失敗させて確認した)
    service.targetGroup.configureHealthCheck({
      path: '/actuator/health',
      healthyHttpCodes: '200',
      interval: cdk.Duration.seconds(30),
      timeout: cdk.Duration.seconds(5),
      // デフォルト5だと healthy 判定まで150秒(30秒×5回)かかり、猶予期間を圧迫するため
      // AWSの最小値である2まで下げる(30秒×2回=60秒で healthy 判定できるようにする)。
      healthyThresholdCount: 2,
    });

    // RDSのセキュリティグループに、FargateタスクのSGからの5432番アクセスのみ許可する
    // (AWS側のSecurityGroupルールdescriptionはASCII文字のみ許可のため英語表記にする)
    database.connections.allowDefaultPortFrom(service.service, 'Allow inbound from Fargate service');

    new cdk.CfnOutput(this, 'ServiceUrl', {
      value: `http://${service.loadBalancer.loadBalancerDnsName}`,
      description: 'ALB経由でアクセスするURL(APIのベースURL)',
    });
  }
}
