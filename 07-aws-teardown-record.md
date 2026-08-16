# AWS環境の撤収記録(2026-08-16)

常時課金(NAT Gateway + RDS + ALB で月$60〜90程度)を止めるため、AWS上で稼働していた
`RecordShopEcMybatisCdkStack` を撤収する。本ドキュメントは**削除直前の状態**と、
**あとから同じ環境を復元するための手順**を記録したもの。

> **完了**: 2026-08-16 07:29 UTC にRDSスナップショットを取得(available)、07:34 UTC に
> `RecordShopEcMybatisCdkStack` の削除を実行し、07:48 UTC までに `DELETE_COMPLETE`。
> 所要時間は約13分。RDSインスタンス・NATゲートウェイ・ALBが消えたことを実リソース側でも確認済み。

インフラ構成そのものの解説は[04-infrastructure-diagram.md](04-infrastructure-diagram.md)を参照。

## 削除直前の稼働状態

| 項目 | 値 |
|---|---|
| アカウント / リージョン | 344246891457 / ap-northeast-1 |
| スタック名 | `RecordShopEcMybatisCdkStack` |
| スタック状態 | `UPDATE_COMPLETE`(作成 2026-08-14、最終更新 2026-08-16 02:28 UTC) |
| 公開URL | https://d10qc58jhjv0fq.cloudfront.net |
| ALB直接URL | http://Record-Recor-KImzb7yjyRT3-313428848.ap-northeast-1.elb.amazonaws.com |
| ECSサービス | `ACTIVE` / desired 1 / running 1、タスク定義リビジョン 8 |
| ALBターゲット | `healthy`(10.0.147.83:8080) |
| RDS | `available`、PostgreSQL 16.13、db.t4g.micro、20GB、Single-AZ |
| VPC | vpc-0cbecd4a29693a7f2 |
| 対応コミット | `record-shop-ec-cdk` の `703f4bc`(SES送信元アドレスを実アドレスに差し替え) |

削除時点でアプリは正常稼働しており(CloudFront経由で HTTP 302 = ログイン画面へのリダイレクトを確認)、
不具合による撤収ではなくコスト都合の停止である。

## 取得したバックアップ

### RDSスナップショット

| 項目 | 値 |
|---|---|
| スナップショットID | `recordshop-mybatis-final-20260816` |
| ARN | `arn:aws:rds:ap-northeast-1:344246891457:snapshot:recordshop-mybatis-final-20260816` |
| 種別 | 手動(manual) ― **自動削除されない** |
| エンジン | PostgreSQL 16.13 |
| サイズ | 20GB(課金は実使用量ベース、月$2程度) |
| 暗号化 | あり(KMSキー `45f0df60-5357-462d-8715-038c13bcca12`) |
| DB名 / マスターユーザー | `recordshop_mybatis` / `recordshop` |

手動スナップショットは**スタックを削除しても残り続ける**(RDSインスタンス本体とは独立したライフサイクル)。
逆に言えば、不要になったら明示的に削除しないとストレージ課金が続く。

> **注意**: スナップショットは暗号化されており、上記のKMSキーはCDKが作ったものではなく
> RDSのデフォルトキー(`aws/rds`)であるため、スタック削除後も残る。復元時に鍵が無くて
> 開けない、という事態にはならない。

## 削除対象のスタック

同一アカウント上のスタックを棚卸しした結果、**削除したのは `RecordShopEcMybatisCdkStack` のみ**。
他の2つは待機コストが発生していないため残した。

| スタック | 内容 | 待機コスト | 判断 |
|---|---|---|---|
| `RecordShopEcMybatisCdkStack` | 本プロジェクト。VPC/RDS/Fargate/ALB/CloudFront | 月$60〜90 | **削除**(常時課金の停止が目的) |
| `TodoMemoStack` | 別プロジェクト(S3+CloudFront+API Gateway+Lambda×2+DynamoDB×2) | ほぼゼロ | 残す。フルサーバーレス構成のため、稼働させたままでも費用が出ない |
| `Infra-ECS-Cluster-api-cluster-6d039fb5` | 2023-07-21にECSコンソールから作られた**空のECSクラスター1個のみ** | ゼロ | 残す。タスクが動いておらずECSクラスター自体は無料 |

コスト削減の実質は `RecordShopEcMybatisCdkStack` 一本であり、他を消しても効果が無いため対象から外した。

なお `TodoMemoStack` のDynamoDBデータ(Todo 2件・メモ1件の動作確認用データ)は、削除を検討した
時点で `C:\AIの作業場\aws-teardown-20260816\` にJSONで退避してある(スタックは残したので現時点では出番はない)。

`CDKToolkit` は**削除していない**。これはCDKのブートストラップスタックで、削除すると
このアカウント・リージョンでCDKデプロイが一切できなくなり、再デプロイ時に
`cdk bootstrap` からやり直しになるため。

## 削除後も残るもの

以下はスタック削除後もアカウントに残る。いずれも待機コストは僅少だが、完全に消したい場合は個別対応が必要。

| リソース | 課金 | 備考 |
|---|---|---|
| RDS手動スナップショット | 月$2程度 | 本記録の復元元。消すと二度と戻せない |
| `CDKToolkit` のS3アセットバケット・ECRリポジトリ | 月数十円〜 | 過去のDockerイメージが蓄積。再デプロイに必要 |
| ECRリポジトリ `api` | 僅少 | `Infra-ECS-Cluster-...` と対だった管理外リソース |
| Secrets Managerのシークレット(RDS認証情報・Adminパスワード) | 削除猶予期間中は僅少 | デフォルト30日後に完全削除。それまでは `restore-secret` で復活可能 |
| SES検証済みID(`osidasi0005@gmail.com`) | 無料 | スタック削除で検証解除される。再デプロイ時は確認メールの再クリックが必要 |

> **重要**: このスタックを削除しても、**アカウント全体の請求はゼロにならない**。
> 姉妹プロジェクトの `AudioShopEcMybatisCdkStack`(オーディオ機材EC)が2026-08-16 07:23 UTCに
> デプロイされており、こちらもVPC+NATゲートウェイ+RDS+Fargate+ALB+CloudFrontという
> **本スタックと同一構成**のため、月$60〜90の課金がそのまま継続する。
> コストを止めたい場合はそちらの扱いも別途判断すること。

## 復元手順

### 1. インフラの再構築

```bash
cd record-shop-ec-cdk && npm ci && npx cdk deploy RecordShopEcMybatisCdkStack
```

Dockerイメージのビルドから始まるため20〜30分程度かかる。完了後、SESの確認メールが
`osidasi0005@gmail.com` に届くのでリンクをクリックして検証を完了させること
(会員登録のメール送信機能がこれに依存している)。

この時点ではDBは**空の状態**で作られ、アプリ起動時のマイグレーションで初期スキーマのみが入る。
デモ用データを流し直すだけでよければ、ここで完了。

### 2. DBデータをスナップショットから戻す場合

CDKが作った空のRDSにスナップショットの中身を上書きすることはできないため、
**スナップショットから別インスタンスとして復元し、そこからデータを移す**のが現実的。

```bash
aws rds restore-db-instance-from-db-snapshot --db-instance-identifier recordshop-restored --db-snapshot-identifier recordshop-mybatis-final-20260816 --db-instance-class db.t4g.micro --region ap-northeast-1
```

復元したインスタンスのマスターパスワードは、**スナップショット取得時点のもの**(削除済みの
Secrets Managerが持っていた自動生成値)が引き継がれる。手元に控えが無いため、復元直後に
リセットする必要がある。

```bash
aws rds modify-db-instance --db-instance-identifier recordshop-restored --master-user-password '<新しいパスワード>' --apply-immediately --region ap-northeast-1
```

あとは `pg_dump` でデータを吸い出し、CDKが作った本番RDSへ `psql` で流し込む。復元用インスタンスは
用が済んだら忘れずに削除すること(放置すると本番と二重に課金される)。

> **なぜパスワードを控えていないか**: 平文パスワードをドキュメントやコードに残さない方針のため
> ([04-infrastructure-diagram.md](04-infrastructure-diagram.md)の「Secrets Manager」の項を参照)。
> RDSは復元後に管理者権限でパスワードをリセットできるので、控えなくても復旧可能である。

## 実行した削除コマンド

```bash
aws cloudformation delete-stack --stack-name RecordShopEcMybatisCdkStack --region ap-northeast-1
```

`cdk destroy` ではなく `delete-stack` を使ったのは、両者が実行する処理は同じ(CDKも内部で
CloudFormationのスタック削除を呼ぶ)一方、`delete-stack` はCDKプロジェクトのディレクトリや
`npm ci` 済みの依存関係を必要とせずどこからでも実行できるため。

CloudFrontディストリビューションの無効化→削除が入るため完了まで20〜40分かかる。進捗確認は以下。

```bash
aws cloudformation list-stacks --region ap-northeast-1 --stack-status-filter DELETE_IN_PROGRESS DELETE_FAILED DELETE_COMPLETE --query "StackSummaries[].{Name:StackName,Status:StackStatus}" --output table
```
