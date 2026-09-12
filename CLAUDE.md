# record_shop_ec_mono

DDD 学習用レコード販売 EC サイトを構成する 5 リポジトリを 1 つにまとめたモノレポ
(`git subtree add` で履歴ごと取り込んだもの)。モノレポにしている理由とディレクトリ構成の詳細は
リポジトリ直下の `README.md` を見る。

| ディレクトリ | 内容 |
| --- | --- |
| `record-shop-ec-mybatis` | アプリ本体(Spring Boot 3.5 / Java 21 / MyBatis / Thymeleaf / Spring Security)。Docker Compose 付き |
| `record-shop-ec-cdk` | AWS インフラ定義(CDK v2 / TypeScript。VPC + RDS + ECS Fargate + ALB + CloudFront) |
| `record-shop-ec-docs` | 設計ドキュメント(ドメイン図・ER図・クラス図・インフラ図・画面仕様など) |
| `record-shop-ec-tests` | システムテスト仕様書と実施記録 |
| `record-shop-ec-spec` | Claude Code で再現するための指示書(phase-1〜7) |

## 検証コマンド(これだけが真実)

AI の「できました」は信じない。次のコマンドが実際に PASS するかどうかだけを見る。

```bash
cd record-shop-ec-mybatis && ./mvnw -B verify
```

```bash
cd record-shop-ec-cdk && npm run build && npm test && npx cdk synth --context env=stage --context imageRef=local
```

## CI が機械的に止めるもの

`.github/workflows/ci.yml` が PR ごとに検査する。**ここに引っかかったら実装が誤りで、検査を緩めない。**

1. `ビルドとテスト`(`record-shop-ec-mybatis`): `./mvnw -B verify`。テストは H2(PostgreSQL 互換モード)を使うため、DB サービスコンテナは無い
2. `インフラのビルドとテスト`(`record-shop-ec-cdk`): `npm run build`(型チェック)/ `npm test`(スタックのテスト)/
   **`npx cdk synth` が AWS 資格情報なしで通ること**(スタックは `env` と `imageRef` のコンテキストを必須にする作りで、
   CI では `--context env=stage --context imageRef=ci-placeholder` を渡す)/
   **生成テンプレート(`cdk.out/*.template.json`)の IAM・SecurityGroup の説明文に非 ASCII 文字が無いこと**
   (synth も型チェックも通るのに CloudFormation が実行時に弾く典型。**説明は英語で書く**)
3. `イメージをビルドする(push しない)`(`record-shop-ec-mybatis`): `docker build` が通ること。**push はしない**
   (ECR も OIDC ロールもまだ無い。build once, deploy many の起点はここに後で足す)

`.github/workflows/security.yml`(Trivy)は依存・Dockerfile・IaC(`record-shop-ec-cdk/infra`。今はまだ無い)・
イメージの脆弱性を見るが、**必須チェックには入れていない**。自分のコードの誤りではなく
「世の中で新しく見つかった脆弱性」で赤くなるため、マージそのものを止めると身動きが取れなくなる。
`.github/workflows/claude-review.yml`(差分レビュー)は `event: COMMENT` 固定で、**マージの門番にしない**
(トークン未登録の環境でも赤くしないゲートが入っている)。

必須チェック名(Ruleset の Required status checks): 「ビルドとテスト」「インフラのビルドとテスト」「差分レビュー」。

## 約束

- **AWS 資格情報は手元にだけ置く。** リポジトリにも、CI やクラウドセッションの環境変数欄にも、チャットにも書かない
- **手元の AWS CLI は Smart App Control に止められる**ため、`amazon/aws-cli` の Docker コンテナで代替する:
  ```
  docker run --rm -v "$env:USERPROFILE\.aws:/root/.aws" amazon/aws-cli <コマンド> --profile <audio-prod|audio-stage>
  ```
- **prod と stage は別の AWS アカウント。** プロファイル名は `audio-prod` / `audio-stage`(アカウント ID はここに書かない)
- **立てたら課金が続く。** 検証が終わったら `cdk destroy` まで実行する
- **Claude レビュー(`claude-review.yml`)は門番にしない。** Dependabot が作った PR は Secret が読めずレビューが
  スキップされるので、**Dependabot の PR は目視で確認してからマージする**
- **既定ブランチは `main`。** Ruleset「main 保護」により PR 必須

## CI/CD の型の出典

- 型そのもの: https://github.com/osidasi0005/cicd_playbook (`docs/pipeline.md`、`docs/decisions.md`、`docs/adoption-checklist.md`)
- 採用するかどうかの判断基準: https://github.com/cosugi-system-organization/development-strategy (private)

## 変更したら追従させるもの

1. `record-shop-ec-docs/04-infrastructure-diagram.md`(インフラ構成を変えたら)
2. `record-shop-ec-tests/system-test-spec.md`(インフラ関連のシステムテストケースに影響するなら)
3. `record-shop-ec-cdk/infra/github-oidc.yaml`(用意した後、Actions から呼ぶ AWS API を増やしたら、**スタックの更新まで**。
   テンプレートを直しただけでは Actions の権限は変わらない)
