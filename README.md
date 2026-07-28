# Chat Payment SaaS

パートナー企業が自社サービスに組み込める、C2C（Consumer-to-Consumer）型の実時間チャット決済プラットフォーム。複数のパートナー企業が同一プラットフォームを共有しながら、顧客データは完全に隔離される（マルチテナント方式）。

---

## 連携アーキテクチャ

パートナー企業サーバーは `X-Api-Key` でチャットルームを生成し、単期セッショントークンを自社ユーザーに伝達する。  
ユーザー（ブラウザ）はセッショントークンのみで WebSocket に接続し、`X-Api-Key` はブラウザに伝達されない。

```
[パートナー企業 サーバー] ──X-Api-Key──▶ POST /api/v1/tenant/chat-rooms
                                  ↓ sessionToken(JWT) 返却
[パートナー企業 フロント] ◀─── トークン ───[パートナー企業 サーバー]
           ↓
[パートナー企業 フロント] ──JWT──▶ WebSocket /ws
```

---

## 技術スタック

| 項目 | 内容 |
|------|------|
| Language | Java 25 (Virtual Threads — JEP 491) |
| Framework | Spring Boot 3.5.9 |
| Build | Gradle 8.14 |
| ORM | Spring Data JPA + QueryDSL 6.12 |
| Realtime | Spring WebSocket (STOMP) |
| DB (開発環境) | H2 in-memory (MySQL互換モード) |

---

## マルチテナントアーキテクチャ

複数のパートナー企業が単一データベースを共有しながら、データは完全に隔離される。

- **Shared DB / Shared Schema** 戦略を採用
- Hibernate 6 の `@TenantId` に基づくRow-level隔離
- すべてのクエリに自動的にパートナーID フィルターを適用

---

## ローカル実行

### 1. `application.yml` を作成

`src/main/resources/application.yml` ファイルを直接作成します（セキュリティのため git 除外）

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:chatpaydb;MODE=MySQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        jdbc:
          batch_size: 50
  h2:
    console:
      enabled: true

jwt:
  secret: your-secret-key-here
  expiration: 86400000
```

### 2. 実行

```bash
./gradlew bootRun
```

アプリケーションは `http://localhost:8080` で起動し、Swagger UI は `http://localhost:8080/swagger-ui.html` で利用可能です。
