# Chat Payment SaaS

パートナー企業が自社サービスに組み込める、オペレーターと購入者による1対1のリアルタイムチャット決済プラットフォーム。複数のパートナー企業が同一プラットフォームを共有しながら、顧客データは完全に隔離される（マルチテナント方式）。

---

## 進捗状況

- [x] チャットメッセージ送受信・WebSocketリアルタイム通信（再接続キャッチアップAPI含む）
- [x] マルチテナント分離・BOLA対策
- [x] 決済リクエスト生成・処理（PENDING → PAID）
- [x] 埋め込みSDK（sdk.js / room-auth.js）
- [ ] Webhook通知（PAID → COMPLETED）
- [ ] 取消・返金API
- [ ] 埋め込みUI（room.html、フロント側で別途実装予定）

---

## 連携アーキテクチャ

パートナー企業サーバーは `X-Api-Key` でチャットルームを生成し、短期セッショントークンを自社ユーザーに伝達する。  
ユーザー（ブラウザ）はセッショントークンのみで WebSocket に接続し、`X-Api-Key` はブラウザに伝達されない。

パートナー企業フロントは `sdk.js` を読み込み、sandboxed `<iframe>`(`room.html`) を生成する。セッショントークンは URL パラメータではなく `postMessage` で iframe に渡され、iframe 内の `room-auth.js` は `document.referrer` との自己一貫性チェックでオリジンを検証してからのみトークンを信頼する。

```mermaid
flowchart TD
    A(["パートナー企業サーバー"]) -->|"X-Api-Key"| B{"API Key 有効?"}
    B -- No --> R1["401 Unauthorized"]
    B -- Yes --> C["sessionToken(JWT)<br/>発行"]
    C --> D(["パートナー企業フロント<br/>sessionToken受信"])
    D --> E["sdk.js: OurChatSDK.open()<br/>sandboxed iframe 生成"]
    E --> F["iframe load完了 →<br/>postMessage<br/>(INIT, sessionToken)"]
    F --> G(["iframe内: room-auth.js"])
    G --> H{"event.origin ==<br/>document.referrer?"}
    H -- No --> R2["無視（何もしない）"]
    H -- Yes --> I["room-auth:ready イベント"]
    I --> J{"WebSocket接続<br/>Bearer JWT 署名検証"}
    J -- 失敗 --> R3["接続拒否<br/>(StompRejectedException)"]
    J -- 成功 --> K["接続許可 + STOMP heartbeat<br/>→ チャットルーム表示"]

    classDef apikey fill:#e1f1ee,stroke:#2c7a6b,color:#185a4d
    classDef sdk fill:#eef0f6,stroke:#656b80,color:#1b1e2b
    classDef jwt fill:#e8eafb,stroke:#3b4fcb,color:#202d8f
    classDef reject fill:#f7e4e9,stroke:#b23a56,color:#7e2338

    class A,B,C apikey
    class D,E,F sdk
    class G,H,I,J,K jwt
    class R1,R2,R3 reject
```

---

## 決済（Trade）フロー

チャットルーム内でオペレーターが決済リクエストを送ると、購入者がそれを承認して決済が完了する。

```mermaid
flowchart TD
    A(["オペレーターフロント"]) -->|"POST /trades"| B{"テナント発信?<br/>(userId == null)"}
    B -- No --> R1["403 SellerOnly"]
    B -- Yes --> C["201 Created (PENDING)<br/>+ WS通知 PAYMENT_REQUEST"]
    C --> D(["購入者フロント"])
    D -->|"POST /trades/{id}/pay"| E{"Wallet残高<br/>十分?"}
    E -- No --> R2["422 InsufficientBalance"]
    E -- Yes --> F["200 OK (PAID)<br/>+ WS通知 PaymentSuccess"]

    classDef seller fill:#eef0f6,stroke:#656b80,color:#1b1e2b
    classDef buyer fill:#e1f1ee,stroke:#2c7a6b,color:#185a4d
    classDef reject fill:#f7e4e9,stroke:#b23a56,color:#7e2338

    class A,B,C seller
    class D,E,F buyer
    class R1,R2 reject
```

チャットルームアクセス検証・決済メッセージ有効性・購入者本人確認など残りのゲートは `docs/ARCHITECTURE.md` に記載している。

---

## 技術スタック

| 項目 | 内容 |
|------|------|
| Language | Java 25 |
| Framework | Spring Boot 3.5.9 |
| Build | Gradle 8.14 |
| ORM | Spring Data JPA + QueryDSL |
| Realtime | Spring WebSocket (STOMP) |
| DB | MySQL 8.4 |

---

## マルチテナントアーキテクチャ

複数のパートナー企業が単一データベースを共有しながら、データは完全に隔離される。

- **Shared DB / Shared Schema** 戦略を採用
- Hibernate 6 の `@TenantId` に基づくRow-level隔離
- すべてのクエリに自動的にパートナーID フィルターを適用

---

## ローカル実行

### 1. MySQL 8.4 を準備

任意の方法（ローカルインストール、Docker、クラウドDBなど）で MySQL 8.4 を用意し、データベースを作成してください。

### 2. `application.yml` を作成

`src/main/resources/application.yml` ファイルを直接作成します（セキュリティのため git 除外）

```yaml
spring:
  sql:
    init:
      mode: always
  datasource:
    url: jdbc:mysql://localhost:3306/your-database-name
    username: your-username
    password: your-password
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: create
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        jdbc:
          batch_size: 50

jwt:
  secret: your-secret-key-here
  expiration-ms: your-expiration-ms-here
```

### 3. 実行

```bash
./gradlew bootRun
```

アプリケーションは `http://localhost:8080` で起動し、Swagger UI は `http://localhost:8080/swagger-ui.html` で利用可能です。
