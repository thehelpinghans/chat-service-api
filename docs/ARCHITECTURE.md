# Architecture

このシステムがなぜこの設計になっているか、実装の背景を記録する。

## 目次

1. [マルチテナント分離](#1-マルチテナント分離)
2. [認証/認可の境界](#2-認証認可の境界)
3. [同時実行制御・ロック戦略](#3-同時実行制御ロック戦略)
4. [エラー応答モデル](#4-エラー応答モデル)
5. [決済の状態遷移](#5-決済の状態遷移)
6. [WebSocketの信頼性](#6-websocketの信頼性)
7. [埋め込みSDKのセキュリティモデル](#7-埋め込みsdkのセキュリティモデル)

---

## 1. マルチテナント分離

Shared DB / Shared Schema 戦略。`BaseEntity`の全エンティティに `@TenantId` を付け、Hibernateの `CurrentTenantIdentifierResolver` にリクエストスコープの値を読ませることで、アプリケーションコードが `WHERE tenant_id = ?` を一切書かずに済む。

```mermaid
flowchart TD
    A1(["テナントサーバー"]) -->|"X-Api-Key"| C["tenant検索"]
    C --> D["TENANT_ATTRIBUTE に<br/>tenantIdを保存"]

    A2(["購入者ブラウザ"]) -->|"Bearer JWT"| E["JWT一回のパースで<br/>tenantId・userId・<br/>chatRoomId抽出"]
    E --> F["RequestAttributes<br/>(SCOPE_REQUEST)に保存"]

    D --> G(["Controller → Service<br/>→ Repository"])
    F --> G
    G --> H["HibernateがSQL生成時に<br/>TenantIdentifierResolver呼び出し"]
    H --> I{"RequestAttributes<br/>存在する?"}
    I -- No(起動時等) --> J["0L 返却<br/>（システムコンテキスト）"]
    I -- Yes --> K{"TENANT_ATTRIBUTE<br/>セット済み?"}
    K -- No --> J
    K -- Yes --> L["セットされた<br/>tenantIdを返却"]
    J --> M["フィルター未適用<br/>（無条件で通過）"]
    L --> N["WHERE tenant_id = ?<br/>自動付加"]

    classDef apikey fill:#e1f1ee,stroke:#2c7a6b,color:#185a4d
    classDef jwt fill:#e8eafb,stroke:#3b4fcb,color:#202d8f
    classDef resolver fill:#eef0f6,stroke:#656b80,color:#1b1e2b
    classDef system fill:#f6ecd6,stroke:#92660f,color:#6b4a08

    class A1,C,D apikey
    class A2,E,F jwt
    class G,H,I,K,L,N resolver
    class J,M system
```

**要点**
- 単一Filterで両方処理: Bearer経路は `tenantId`・`userId`・`chatRoomId` が同じJWTから出るため、別Filterに分けると署名検証の重複と `@Order` 依存の暗黙結合が発生 → あえて統合
- `RequestAttributes(SCOPE_REQUEST)`: リクエスト完了時にSpringが自動破棄、手動 `clear()` 不要
- `0L` フォールバック: 起動時のシステムコンテキストと、`X-Api-Key` 検索時点（まだ `tenantId` 未セット）の2箇所で発生

---

## 2. 認証/認可の境界

```mermaid
flowchart TD
    A(["テナントサーバー"]) -->|"X-Api-Key,<br/>POST /tokens<br/>(externalUserId)"| B{"chatRoom所有者<br/>と一致?"}
    B -- No --> R1["403<br/>ChatRoomAccessDenied"]
    B -- Yes --> D["JWT発行<br/>(userId あり)"]

    A -->|"X-Api-Key,<br/>POST /tenant-tokens"| E["JWT発行<br/>(userId = null)"]

    D --> F(["購入者フロント /<br/>オペレーターフロント"])
    E --> F
    F -->|"Bearer JWT,<br/>/api/v1/user/**"| G["TenantFilterが<br/>tenantId・userId・<br/>chatRoomId抽出"]

    G --> H{"REST: 経路の<br/>chatRoomIdと一致?"}
    H -- No --> R2["403<br/>ChatRoomAccessDenied"]
    H -- Yes --> I["リクエスト処理"]

    G --> J{"WS SUBSCRIBE:<br/>/topic/chat/{id}と一致?"}
    J -- No --> R3["購読拒否<br/>(StompRejectedException)"]
    J -- Yes --> K["購読許可"]

    classDef issue fill:#e1f1ee,stroke:#2c7a6b,color:#185a4d
    classDef verify fill:#e8eafb,stroke:#3b4fcb,color:#202d8f
    classDef reject fill:#f7e4e9,stroke:#b23a56,color:#7e2338

    class A,B,D,E issue
    class F,G,H,I,J,K verify
    class R1,R2,R3 reject
```

**要点**
- オペレータートークン(`issueTenantToken`)は所有権チェック不要: `userId = null` で「誰の物」ではなく「テナント所属」だけ証明すればよく、それは発行時点の `X-Api-Key` で既に検証済み
- 購入者トークン(`issueUserToken`)のみ所有権チェック: 発行時点で弾けば、不正な `chatRoomId` を指したトークン自体が生まれない
- REST・WS両方とも同じ原理でBOLA対策: JWT claimの `chatRoomId` を「そのトークンでアクセス可能な唯一の部屋」として扱い、経路変数／購読destinationと突き合わせる
- WSはCONNECT時にclaimをセッションに保存し、SUBSCRIBE時に突き合わせる: 接続と購読が別イベントのため、一時保存する仕組みが必要
- 統合契約の境界: 発行したトークンをテナントサーバーからテナントフロントへ渡す方法（SSR埋め込み、自前APIなど）はテナント側の実装次第で当システムの管轄外。`sdk.js`の`OurChatSDK.open({ sessionToken, ... })`はテナントフロントが既にトークンを持っている前提で呼ばれる

---

## 3. 同時実行制御・ロック戦略

```mermaid
flowchart TD
    A1(["リクエストA"]) --> C{"findByUserIdAndItemId<br/>既存?"}
    A2(["リクエストB<br/>(同時)"]) --> C
    C -- 両方とも空 --> D["両方ともsave()試行"]
    D --> P["勝者:<br/>save()成功 → 201"]
    D --> E["敗者:<br/>DB一意制約違反"]
    E --> F["409<br/>(DataIntegrity-<br/>ViolationException)"]
    F --> G["同じPUTを再試行"]
    G --> H["勝者の行を発見<br/>→ 200"]

    B1(["リクエストA'"]) --> K["Wallet・Tradeを取得<br/>(同じversion)"]
    B2(["リクエストB'<br/>(同時, 同じchatMessageId)"]) --> K
    K --> L["先にcommit:<br/>pay()成功、version+1"]
    K --> M["後でcommit:<br/>version不一致"]
    M --> N["409<br/>(ObjectOptimistic-<br/>LockingFailureException)"]

    classDef room fill:#e1f1ee,stroke:#2c7a6b,color:#185a4d
    classDef trade fill:#e8eafb,stroke:#3b4fcb,color:#202d8f
    classDef reject fill:#f7e4e9,stroke:#b23a56,color:#7e2338
    classDef ok fill:#eef0f6,stroke:#656b80,color:#1b1e2b

    class A1,A2,C,D,E,P room
    class B1,B2,K,L trade
    class F,M,N reject
    class G,H ok
```

**要点**
- ChatRoom生成競合: サービス層でcatch/再照会を一切しない — DB一意制約違反をそのまま伝播させ、`GlobalExceptionHandler`が409に統一
- 409を受けたら同じPUTを再試行する — 409が返る時点で勝者のコミットは既に完了しているため、再試行は200で確定する
- 冪等性の根拠: RFC 9110の冪等性定義は応答コードではなく「サーバーへの効果」が基準 — 201でも(再試行後の)200でも、最終的にその部屋が1つだけ存在するという状態は変わらないため冪等
- Trade決済の同時実行: WalletとTrade両方に`@Version` — 後発コミットはversion不一致で失敗し、二重決済・二重引き落としを防ぐ
- 再試行はクライアントの責任: サーバー側は楽観ロック失敗をそのまま409として返すだけ

---

## 4. エラー応答モデル

```mermaid
flowchart TD
    A(["リクエスト処理中に失敗"]) --> B{"ビジネス/セキュリティ<br/>判断?"}
    B -- "Yes<br/>(BOLA・not-found・<br/>状態異常など)" --> C["Serviceがsealed<br/>interfaceのType<br/>(reason付き)を返す"]
    C --> D["Controllerのswitchが<br/>分岐ごとのHTTP<br/>ステータス+reasonを返却"]
    D --> E["4xx + reason"]

    B -- "No<br/>(真のインフラ問題)" --> F["例外がそのまま伝播"]
    F --> G["GlobalExceptionHandler"]
    G --> H["DataIntegrityViolation/<br/>OptimisticLocking"]
    H --> I["409(bodyなし)"]
    G --> J["IllegalArgument/<br/>IllegalState/<br/>DataAccess/Exception"]
    J --> K["500 + 汎用メッセージ<br/>(詳細はlogのみ)"]

    classDef business fill:#e1f1ee,stroke:#2c7a6b,color:#185a4d
    classDef infra fill:#e8eafb,stroke:#3b4fcb,color:#202d8f
    classDef decision fill:#eef0f6,stroke:#656b80,color:#1b1e2b

    class C,D,E business
    class F,G,H,I,J,K infra
    class A,B decision
```

**要点**
- ビジネス/セキュリティ判断は例外ではない: BOLAチェックなどは「バグ」ではなく正常な判断のため、値(sealed interface)で表現する。switch式は全permitsを尽くさないとコンパイルエラーになるため、新しい失敗ケースを追加した時の対応漏れが構造的に起きない
- Controllerのswitchは受け取った型に応じたHTTPステータス+reasonを直接返却する
- `GlobalExceptionHandler`は大きく2種を扱う: 同時実行の衝突(409)と、それ以外の予期しないバグ/インフラ例外(500、詳細はlogのみ) — 業務ロジックとは重ならない
- WebSocketも同じ構成: `MessageDeliveryException`(Spring)を`StompRejectedException`(独自)に置換、クライアントには一般化したメッセージのみ返す

---

## 5. 決済の状態遷移

```mermaid
flowchart TD
    A(["Trade作成<br/>(createTrade)"]) --> B["PENDING"]
    B --> C{"payTrade:<br/>tradeStatus==PENDING?"}
    C -- No --> D["409<br/>PaymentAlreadyProcessed"]
    C -- Yes --> E["Wallet.pay() +<br/>changeStatus(PAID)"]
    E --> F["PAID"]

    classDef state fill:#e1f1ee,stroke:#2c7a6b,color:#185a4d
    classDef guard fill:#e8eafb,stroke:#3b4fcb,color:#202d8f
    classDef reject fill:#f7e4e9,stroke:#b23a56,color:#7e2338

    class A,B,F state
    class C,E guard
    class D reject
```

**要点**
- `TradeStatus`は5値宣言済み（PENDING/PAID/COMPLETED/CANCELLED/REFUNDED）だが、実装済みの遷移はPENDING→PAIDのみ
- 「すでに処理済み」を防ぐ層が2つある理由: `checkPayableTrade`は連続的な二重実行（例: 支払い済みのボタンをもう一度押す）を防ぐが、同時に来た2件までは防げない。本当に同時に来たリクエストの競合は3番の`@Version`が拾う — 前者はreason付き409、後者はbodyなし409
- `Trade.changeStatus()`は`tradeStatus != PENDING`なら`IllegalStateException`を投げる — 不変式をエンティティ自身に持たせることで、呼び出し元が増えても保護に漏れが生じない

---

## 6. WebSocketの信頼性

```mermaid
flowchart TD
    A(["WebSocket接続中<br/>(STOMP heartbeat 10s/10s)"]) --> B["ハートビート途絶<br/>→ 接続断とみなす"]
    B --> C["クライアントが再接続<br/>(同じBearer JWTで<br/>CONNECT)"]
    C --> D{"JWT有効?"}
    D -- No --> E["接続拒否<br/>(StompRejectedException)"]
    D -- Yes --> F["GET /messages/sync<br/>?afterMessageId=X"]
    F --> G["切断中に欠落した<br/>メッセージを取得"]

    classDef normal fill:#eef0f6,stroke:#656b80,color:#1b1e2b
    classDef detect fill:#e8eafb,stroke:#3b4fcb,color:#202d8f
    classDef recover fill:#e1f1ee,stroke:#2c7a6b,color:#185a4d
    classDef reject fill:#f7e4e9,stroke:#b23a56,color:#7e2338

    class A normal
    class B,C,D detect
    class F,G recover
    class E reject
```

**要点**
- ハートビートがWebSocket特有の「沈黙した切断」を検知する唯一の手段: TCP接続は切れれば即座にわかり、メッセージだけ静かに消えることはない
- 再接続後は新しいトークンの発行が不要 — 同じBearer JWTをそのまま`CONNECT`に再利用し、`GET /messages/sync`で切断中のメッセージだけ取り戻す
- `MessageDeliveryException`(Spring)ではなく`StompRejectedException`(独自)を使う理由: SpringはSTOMP ERRORフレームの`message`ヘッダーに例外メッセージをそのまま載せてクライアントへ送る — RESTなら`GlobalExceptionHandler`が常に一般化した文言に包み直すが、STOMPにはその保護がない。将来メッセージに内部詳細（署名検証の失敗理由など）を書いてしまうと、そのままクライアントへ漏れる。`StompRejectedException`に置き換えることで、返す文言を`MessageResolver`経由の一般化されたものに固定する

---

## 7. 埋め込みSDKのセキュリティモデル

```mermaid
flowchart TD
    A(["sdk.js:<br/>OurChatSDK.open()"]) --> B["iframe生成 +<br/>sandbox属性を付与"]
    B --> C["allow-scripts・<br/>allow-forms・<br/>allow-top-navigation-<br/>by-user-activation・<br/>allow-same-origin"]
    C --> D["ユーザー操作を伴わない<br/>強制的な画面遷移は不可"]

    A --> E["iframe.srcには<br/>roomId(非機密)のみ"]
    E --> F["sessionTokenは<br/>URLに含めない"]
    F --> G["postMessageのみで<br/>iframeへ渡す"]

    classDef sandbox fill:#eef0f6,stroke:#656b80,color:#1b1e2b
    classDef token fill:#e1f1ee,stroke:#2c7a6b,color:#185a4d

    class A,B,C,D sandbox
    class E,F,G token
```

**要点**
- iframeに`sandbox`属性: `allow-top-navigation-by-user-activation`により、ユーザー操作を伴わない強制的なページ遷移（クリックジャッキング等の一部手法）を防ぐ
- `sessionToken`はURLに含めない: `iframe.src`にはroomId（非機密）のみ渡し、トークンはpostMessageでのみ送る — URLはブラウザ履歴やRefererヘッダーに残ってしまうため
- postMessage送受信時のorigin検証は`README.md`の「連携アーキテクチャ」図を参照 — `targetOrigin`を`SDK_ORIGIN`に限定し、受信側は`document.referrer`との自己一貫性を確認する
