#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib/core';
import {
  RecordShopEcMybatisCdkStack,
  resolveDeployEnvironment,
  resolveImageRef,
  stackNameFor,
} from '../lib/record-shop-ec-mybatis-cdk-stack';

const app = new cdk.App();

// コンテキスト env(必須)で prod / stage を決める。未指定・不正値はここでエラーにして止める
// (→ resolveDeployEnvironment)。
const environmentName = resolveDeployEnvironment(app);

// コンテキスト imageRef(必須)。ECR の SHA タグ、または sha256: 始まりのダイジェスト。
const imageRef = resolveImageRef(app);

/**
 * env に応じて 1 スタックだけ作る。
 *
 * prod と stage の両方を常に定義すると、スタック名を省いた `cdk deploy` が
 * 両方に一度に出てしまう。env ごとに 1 スタックだけを app に載せることで、
 * 「そのコマンドがどの環境に触るか」を env の指定自体で決め切る。
 *
 * account/region は渡さない(env-agnostic)。fromLookup を使わない構成なので、
 * `cdk synth` は AWS 資格情報が無くても通る。
 */
new RecordShopEcMybatisCdkStack(app, stackNameFor(environmentName), {
  environmentName,
  imageRef,
});
