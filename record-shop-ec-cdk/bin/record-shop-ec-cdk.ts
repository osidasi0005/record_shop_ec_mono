#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib/core';
import { RecordShopEcMybatisCdkStack } from '../lib/record-shop-ec-mybatis-cdk-stack';

const app = new cdk.App();
const env = {
  account: process.env.CDK_DEFAULT_ACCOUNT,
  region: process.env.CDK_DEFAULT_REGION,
};

new RecordShopEcMybatisCdkStack(app, 'RecordShopEcMybatisCdkStack', { env });
