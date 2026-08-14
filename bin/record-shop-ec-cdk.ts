#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib/core';
import { RecordShopEcCdkStack } from '../lib/record-shop-ec-cdk-stack';
import { RecordShopEcMybatisCdkStack } from '../lib/record-shop-ec-mybatis-cdk-stack';

const app = new cdk.App();
const env = {
  account: process.env.CDK_DEFAULT_ACCOUNT,
  region: process.env.CDK_DEFAULT_REGION,
};

new RecordShopEcCdkStack(app, 'RecordShopEcCdkStack', { env });
// JPA版との比較用。同じドメインモデル・Web層をMyBatisで永続化した版を、
// 別スタック(別VPC/RDS/ECS/ALB/CloudFront)としてAWS上にも並行稼働させる。
new RecordShopEcMybatisCdkStack(app, 'RecordShopEcMybatisCdkStack', { env });
