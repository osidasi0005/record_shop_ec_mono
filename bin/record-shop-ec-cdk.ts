#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib/core';
import { RecordShopEcCdkStack } from '../lib/record-shop-ec-cdk-stack';

const app = new cdk.App();
new RecordShopEcCdkStack(app, 'RecordShopEcCdkStack', {
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.CDK_DEFAULT_REGION,
  },
});
