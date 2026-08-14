import * as cdk from 'aws-cdk-lib/core';
import { Construct } from 'constructs';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecs_patterns from 'aws-cdk-lib/aws-ecs-patterns';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as origins from 'aws-cdk-lib/aws-cloudfront-origins';
import * as path from 'path';

/**
 * record-shop-ec-jpa(JPA版)と同じドメインモデル・Web層を持つMyBatis版の
 * AWSインフラ。{@link RecordShopEcCdkStack}をそのまま複製し、参照するDockerfileだけを
 * record-shop-ec-mybatisに差し替えている(VPC/RDS/ECS/ALB/CloudFrontをJPA版とは
 * 別に1セットまるごと持つ ―― AWS上でもJPA版・MyBatis版を同時に動かして比較できるようにするため)。
 *
 * コスト面は{@link RecordShopEcCdkStack}と同一(NATゲートウェイ1個、Fargate 0.25vCPU/0.5GB x1台、
 * RDS db.t4g.micro)。JPA版と2セット同時稼働させると費用も単純に2倍(月$120〜180程度)になるため、
 * 比較検証が終わったら `cdk destroy RecordShopEcMybatisCdkStack` で削除すること。
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

    // record-shop-ec-mybatis の Dockerfile からイメージをビルドし、ECRへ自動プッシュする。
    const service = new ecs_patterns.ApplicationLoadBalancedFargateService(this, 'RecordShopMybatisService', {
      cluster,
      cpu: 256,
      memoryLimitMiB: 512,
      desiredCount: 1,
      publicLoadBalancer: true,
      // JPA版で実際にクラッシュループを起こして確認した値をそのまま踏襲する。
      healthCheckGracePeriod: cdk.Duration.seconds(300),
      taskImageOptions: {
        image: ecs.ContainerImage.fromAsset(path.join(__dirname, '../../record-shop-ec-mybatis')),
        containerPort: 8080,
        environment: {
          DB_HOST: database.instanceEndpoint.hostname,
          DB_PORT: database.instanceEndpoint.port.toString(),
          DB_NAME: 'recordshop_mybatis',
          ADMIN_EMAIL: 'admin@example.com',
        },
        secrets: {
          DB_USERNAME: ecs.Secret.fromSecretsManager(database.secret!, 'username'),
          DB_PASSWORD: ecs.Secret.fromSecretsManager(database.secret!, 'password'),
          ADMIN_PASSWORD: ecs.Secret.fromSecretsManager(adminSecret, 'password'),
        },
      },
    });

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
