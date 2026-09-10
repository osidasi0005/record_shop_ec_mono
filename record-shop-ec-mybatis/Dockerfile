# ---- Build stage ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# 依存関係の解決だけ先に行い、Dockerのレイヤーキャッシュを効かせる
# (pom.xml が変わらない限り、ソース変更のたびに毎回全依存をダウンロードし直さずに済む)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -q package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# root で実行しない
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/target/record-shop-ec-mybatis-*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
