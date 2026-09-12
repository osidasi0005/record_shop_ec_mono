import * as cdk from 'aws-cdk-lib/core';
import { Match, Template } from 'aws-cdk-lib/assertions';
import {
  DeployEnvironment,
  RecordShopEcMybatisCdkStack,
  executionRoleNameFor,
  resolveDeployEnvironment,
  resolveImageRef,
  stackNameFor,
  taskRoleNameFor,
} from '../lib/record-shop-ec-mybatis-cdk-stack';

/** テスト用に env / imageRef のコンテキストを渡した App から、スタックのテンプレートを作る。 */
function synthesize(environmentName: DeployEnvironment, imageRef = 'abc1234') {
  const app = new cdk.App({ context: { env: environmentName, imageRef } });
  const stack = new RecordShopEcMybatisCdkStack(app, stackNameFor(environmentName), {
    env: { account: '123456789012', region: 'ap-northeast-1' },
    environmentName,
    imageRef,
  });
  return Template.fromStack(stack);
}

test('RDS(PostgreSQL)がプライベートサブネットに1台作られる', () => {
  const template = synthesize('prod');

  template.hasResourceProperties('AWS::RDS::DBInstance', {
    Engine: 'postgres',
    DBInstanceClass: 'db.t4g.micro',
    PubliclyAccessible: false,
  });
});

test('ECS FargateサービスがALB経由で公開される', () => {
  const template = synthesize('prod');

  template.resourceCountIs('AWS::ECS::Service', 1);
  template.resourceCountIs('AWS::ElasticLoadBalancingV2::LoadBalancer', 1);
  template.hasResourceProperties('AWS::ElasticLoadBalancingV2::LoadBalancer', {
    Scheme: 'internet-facing',
  });
});

test('VPCにNATゲートウェイが1個だけ作られる(コスト最小化)', () => {
  const template = synthesize('prod');

  template.resourceCountIs('AWS::EC2::NatGateway', 1);
});

test('CloudFrontがALBの前段に1つ作られ、HTTPSへリダイレクトする', () => {
  const template = synthesize('prod');

  template.resourceCountIs('AWS::CloudFront::Distribution', 1);
  template.hasResourceProperties('AWS::CloudFront::Distribution', {
    DistributionConfig: {
      DefaultCacheBehavior: {
        ViewerProtocolPolicy: 'redirect-to-https',
      },
    },
  });
});

test('管理者パスワード用のSecrets Managerシークレットが作られる', () => {
  const template = synthesize('prod');

  template.resourceCountIs('AWS::SecretsManager::Secret', 2); // RDS用 + Admin用
});

test('コンテキスト env が未指定だとエラーで落ちる', () => {
  const app = new cdk.App({ context: { imageRef: 'abc1234' } });
  expect(() => resolveDeployEnvironment(app)).toThrow();
});

test('コンテキスト env が prod/stage 以外だとエラーで落ちる', () => {
  const app = new cdk.App({ context: { env: 'production', imageRef: 'abc1234' } });
  expect(() => resolveDeployEnvironment(app)).toThrow();
});

test('コンテキスト imageRef が未指定だとエラーで落ちる', () => {
  const app = new cdk.App({ context: { env: 'prod' } });
  expect(() => resolveImageRef(app)).toThrow();
});

test('スタック名が env で変わる(prod は接尾辞なし、stage は Stage 付き)', () => {
  expect(stackNameFor('prod')).toBe('RecordShopEcMybatisCdkStack');
  expect(stackNameFor('stage')).toBe('RecordShopEcMybatisCdkStackStage');

  const prodTemplate = synthesize('prod');
  const stageTemplate = synthesize('stage');

  // 両方とも同じ構成であること(タスク定義・サービスの数など)を確かめる。
  prodTemplate.resourceCountIs('AWS::ECS::Service', 1);
  stageTemplate.resourceCountIs('AWS::ECS::Service', 1);
});

test.each<[string, string]>([
  ['SHAタグ', 'a1b2c3d4e5f6'],
  ['ダイジェスト', 'sha256:0000000000000000000000000000000000000000000000000000000000000000'],
])('imageRef が%sでも受かる', (_label, imageRef) => {
  const template = synthesize('prod', imageRef);

  template.hasOutput('AppImageRef', { Value: imageRef });
});

test('タスクロール・実行ロールの RoleName が環境ごとに期待どおりになる', () => {
  const prodTemplate = synthesize('prod');
  const stageTemplate = synthesize('stage');

  expect(taskRoleNameFor('prod')).toBe('record-shop-ec-task-prod');
  expect(taskRoleNameFor('stage')).toBe('record-shop-ec-task-stage');
  expect(executionRoleNameFor('prod')).toBe('record-shop-ec-exec-prod');
  expect(executionRoleNameFor('stage')).toBe('record-shop-ec-exec-stage');

  prodTemplate.hasResourceProperties('AWS::IAM::Role', {
    RoleName: 'record-shop-ec-task-prod',
  });
  prodTemplate.hasResourceProperties('AWS::IAM::Role', {
    RoleName: 'record-shop-ec-exec-prod',
  });
  stageTemplate.hasResourceProperties('AWS::IAM::Role', {
    RoleName: 'record-shop-ec-task-stage',
  });
  stageTemplate.hasResourceProperties('AWS::IAM::Role', {
    RoleName: 'record-shop-ec-exec-stage',
  });
});

test('ECSサービスのデプロイサーキットブレーカーが有効', () => {
  const template = synthesize('prod');

  template.hasResourceProperties('AWS::ECS::Service', {
    DeploymentConfiguration: {
      DeploymentCircuitBreaker: { Enable: true, Rollback: true },
      MinimumHealthyPercent: 100,
      MaximumPercent: 200,
    },
  });
});

test('スタックにタグ record-shop-ec:stack-name が付いている', () => {
  const prodTemplate = synthesize('prod');
  const stageTemplate = synthesize('stage');

  prodTemplate.hasResourceProperties('AWS::EC2::VPC', {
    Tags: Match.arrayWith([
      { Key: 'record-shop-ec:stack-name', Value: 'RecordShopEcMybatisCdkStack' },
    ]),
  });
  stageTemplate.hasResourceProperties('AWS::EC2::VPC', {
    Tags: Match.arrayWith([
      { Key: 'record-shop-ec:stack-name', Value: 'RecordShopEcMybatisCdkStackStage' },
    ]),
  });
});

test('IAM ロールの説明文は ASCII のみ', () => {
  const template = synthesize('prod');
  const roles = template.findResources('AWS::IAM::Role');

  for (const role of Object.values(roles)) {
    const description = (role as { Properties?: { Description?: string } }).Properties
      ?.Description;
    if (typeof description === 'string') {
      expect(description).toMatch(/^[\x00-\x7F]*$/);
    }
  }
});
