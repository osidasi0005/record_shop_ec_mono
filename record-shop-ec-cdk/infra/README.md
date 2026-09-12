# infra/ — GitHub Actions が AWS を触るための土台

CDK アプリの外に置く CloudFormation テンプレート 2 本。**一度だけ、手元から管理者資格情報で**流す。
prod と stage は別の AWS アカウントなので、それぞれのアカウントで 1 回ずつ。
出典は [cicd_playbook の templates/aws](https://github.com/osidasi0005/cicd_playbook/tree/master/templates/aws)(固有名を埋めたもの)。

| ファイル | 作るもの |
|---|---|
| `ecr-repository.yaml` | アプリのイメージ用 ECR リポジトリ `record-shop-ec-mybatis`(イミュータブル、push 時スキャン、Retain)。stage 側は prod のデプロイロールに pull を許可 |
| `github-oidc.yaml` | デプロイロール `record-shop-ec-github-deploy`(`staging` / `production` の Environment を信頼)と、stage だけ ECR push ロール `record-shop-ec-github-ecr-push`(`main` への push だけを信頼) |

OIDC プロバイダはアカウントに 1 つで、両アカウントとも別プロジェクトが作成済み。`CreateOidcProvider=false` で既存の ARN を渡す。

## 手元の AWS CLI について

この PC では AWS CLI が Smart App Control に止められるので、`amazon/aws-cli` のコンテナで代替する(Docker Desktop を先に起動)。
SSO のログインはブラウザ承認が要る。

```powershell
docker run --rm -it -v "$env:USERPROFILE\.aws:/root/.aws" amazon/aws-cli sso login --profile audio-stage --use-device-code
```

## 適用する順番

stage の ECR リポジトリポリシーが prod のデプロイロール ARN を要るので、この順。`<...>` は自分の値に置き換える(アカウント ID は public リポジトリなのでここに書かない)。

1. **prod: ECR**

   ```powershell
   docker run --rm -e AWS_CLI_FILE_ENCODING=UTF-8 -v "$env:USERPROFILE\.aws:/root/.aws" -v "<このディレクトリの絶対パス>:/work" -w /work amazon/aws-cli cloudformation deploy --profile audio-prod --region ap-northeast-1 --template-file ecr-repository.yaml --stack-name record-shop-ec-ecr --parameter-overrides RepositoryName=record-shop-ec-mybatis TargetEnvironment=prod
   ```

2. **prod: OIDC ロール**(出力 `DeployRoleArn` を控える)

   ```powershell
   docker run --rm -e AWS_CLI_FILE_ENCODING=UTF-8 -v "$env:USERPROFILE\.aws:/root/.aws" -v "<このディレクトリの絶対パス>:/work" -w /work amazon/aws-cli cloudformation deploy --profile audio-prod --region ap-northeast-1 --template-file github-oidc.yaml --stack-name record-shop-ec-github-oidc --capabilities CAPABILITY_NAMED_IAM --parameter-overrides TargetEnvironment=prod CreateOidcProvider=false ExistingOidcProviderArn=arn:aws:iam::<prod account id>:oidc-provider/token.actions.githubusercontent.com StageAccountId=<stage account id>
   ```

3. **stage: OIDC ロール**(出力 `DeployRoleArn` と `EcrPushRoleArn` を控える)

   ```powershell
   docker run --rm -e AWS_CLI_FILE_ENCODING=UTF-8 -v "$env:USERPROFILE\.aws:/root/.aws" -v "<このディレクトリの絶対パス>:/work" -w /work amazon/aws-cli cloudformation deploy --profile audio-stage --region ap-northeast-1 --template-file github-oidc.yaml --stack-name record-shop-ec-github-oidc --capabilities CAPABILITY_NAMED_IAM --parameter-overrides TargetEnvironment=stage CreateOidcProvider=false ExistingOidcProviderArn=arn:aws:iam::<stage account id>:oidc-provider/token.actions.githubusercontent.com
   ```

4. **stage: ECR**(手順 2 の `DeployRoleArn` を渡す)

   ```powershell
   docker run --rm -e AWS_CLI_FILE_ENCODING=UTF-8 -v "$env:USERPROFILE\.aws:/root/.aws" -v "<このディレクトリの絶対パス>:/work" -w /work amazon/aws-cli cloudformation deploy --profile audio-stage --region ap-northeast-1 --template-file ecr-repository.yaml --stack-name record-shop-ec-ecr --parameter-overrides RepositoryName=record-shop-ec-mybatis TargetEnvironment=stage ProdDeployRoleArn=<手順 2 の DeployRoleArn>
   ```

出力の読み方:

```powershell
docker run --rm -v "$env:USERPROFILE\.aws:/root/.aws" amazon/aws-cli cloudformation describe-stacks --profile audio-stage --region ap-northeast-1 --stack-name record-shop-ec-github-oidc --query "Stacks[0].Outputs" --output table
```

## GitHub 側に登録する値

Settings → Environments / Secrets and variables → Actions。ARN とレジストリのホスト名は秘密ではないので Variables。

| 登録先 | 名前 | 値 |
|---|---|---|
| Environment `production` | `AWS_DEPLOY_ROLE_ARN` | 手順 2 の `DeployRoleArn` |
| Environment `staging` | `AWS_DEPLOY_ROLE_ARN` | 手順 3 の `DeployRoleArn` |
| リポジトリ変数 | `AWS_ECR_PUSH_ROLE_ARN` | 手順 3 の `EcrPushRoleArn` |
| リポジトリ変数 | `AWS_REGION` | `ap-northeast-1` |
| Environment `production` | `STAGE_ECR_REGISTRY` | `<stage account id>.dkr.ecr.ap-northeast-1.amazonaws.com` |
| Environment `production` | `PROD_ECR_REGISTRY` | `<prod account id>.dkr.ecr.ap-northeast-1.amazonaws.com` |

## 変更したら

- Actions から呼ぶ AWS API を増やしたら、`github-oidc.yaml` を直したうえで **両アカウントでスタックを更新する**まで行う。テンプレートを直しただけでは権限は変わらず、手元からは通るのに Actions からだけ拒否される形で出る
- IAM の `Description` は ASCII のみ。`node <cicd_playbook>/templates/aws/scripts/check-infra-templates.mjs record-shop-ec-cdk/infra` で検査できる
- `Mappings` の `TaskRoleName` / `ExecutionRoleName` は `lib/` のロール名と一致していること。ずれるとロールバック用の `PassRole` だけが Actions で落ちる

## 消し方

ECR は `DeletionPolicy: Retain` なので、スタックを消してもリポジトリは残る。本当に消すときはリポジトリを空にしてから `aws ecr delete-repository --repository-name record-shop-ec-mybatis --force`。
OIDC のスタックは `cloudformation delete-stack --stack-name record-shop-ec-github-oidc`(プロバイダ自体は他プロジェクトのものなので消えない)。
