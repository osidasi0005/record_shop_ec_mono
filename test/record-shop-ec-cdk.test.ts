import * as cdk from 'aws-cdk-lib/core';
import { Template } from 'aws-cdk-lib/assertions';
import { RecordShopEcCdkStack } from '../lib/record-shop-ec-cdk-stack';

function synthesize() {
  const app = new cdk.App();
  const stack = new RecordShopEcCdkStack(app, 'TestStack', {
    env: { account: '123456789012', region: 'ap-northeast-1' },
  });
  return Template.fromStack(stack);
}

test('RDS(PostgreSQL)がプライベートサブネットに1台作られる', () => {
  const template = synthesize();

  template.hasResourceProperties('AWS::RDS::DBInstance', {
    Engine: 'postgres',
    DBInstanceClass: 'db.t4g.micro',
    PubliclyAccessible: false,
  });
});

test('ECS FargateサービスがALB経由で公開される', () => {
  const template = synthesize();

  template.resourceCountIs('AWS::ECS::Service', 1);
  template.resourceCountIs('AWS::ElasticLoadBalancingV2::LoadBalancer', 1);
  template.hasResourceProperties('AWS::ElasticLoadBalancingV2::LoadBalancer', {
    Scheme: 'internet-facing',
  });
});

test('VPCにNATゲートウェイが1個だけ作られる(コスト最小化)', () => {
  const template = synthesize();

  template.resourceCountIs('AWS::EC2::NatGateway', 1);
});

test('CloudFrontがALBの前段に1つ作られ、HTTPSへリダイレクトする', () => {
  const template = synthesize();

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
  const template = synthesize();

  template.resourceCountIs('AWS::SecretsManager::Secret', 2); // RDS用 + Admin用
});
