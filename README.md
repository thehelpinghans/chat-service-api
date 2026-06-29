# Chat Payment SaaS

파트너사가 자사 서비스에 임베드하여 사용자 간 채팅·거래·결제 흐름을 제공하는 C2C 결제 플랫폼.

---

## 배경 및 요구사항

C2C 거래 서비스에서 핵심 흐름은 **상품 문의 → 가격 협의 → 결제**다. 이 흐름이 채팅 밖으로 나가면 이탈률이 높아진다.

이 플랫폼은 다음 요구사항을 충족한다.

- 파트너사가 자신의 서비스에 채팅·결제를 빠르게 임베드할 수 있어야 한다
- 여러 파트너사가 하나의 플랫폼을 공유하되 데이터는 완전히 격리되어야 한다
- 채팅 메시지가 실시간으로 전달되어야 한다
- 채팅방 안에서 결제 요청·승인이 이루어져야 한다

---

## 기술 선택 배경

### 채팅 메시지 실시간 전달
채팅 메시지는 전송 즉시 상대방에게 도달해야 한다. HTTP 요청-응답 모델로는 서버가 먼저 메시지를 클라이언트에 보낼 수 없다.
→ **WebSocket (STOMP)** 으로 클라이언트-서버 간 지속 연결을 유지하여 서버 푸시 지원

### 수천 개의 동시 WebSocket 연결 처리
채팅 서비스는 사용자마다 WebSocket 연결을 유지한다. OS 스레드 모델은 스레드당 메모리 비용이 높아 동시 접속이 늘수록 한계가 생긴다.
→ **Java Virtual Threads** 로 스레드 비용을 낮추고 동시 연결 수 확장

### 파트너사별 데이터 완전 격리
여러 파트너사가 동일 플랫폼을 사용하므로 데이터가 섞이면 안 된다. 파트너사마다 별도 DB를 두면 운영 비용이 급증한다.
→ **MySQL + Hibernate @TenantId** 로 단일 DB에서 Row-level 격리, 모든 쿼리에 파트너사 ID 필터 자동 적용

### 채팅 이력의 복잡한 동적 조회
커서 기반 페이지네이션, 채팅방 필터링 등 동적 조건이 많다. JPQL 문자열 조합은 타입 안전하지 않고 유지보수가 어렵다.
→ **QueryDSL** 로 컴파일 타임에 오류를 잡는 타입 안전한 동적 쿼리 작성

### 파트너사 개발자 연동 지원
파트너사 개발자가 API 명세를 보고 빠르게 연동할 수 있어야 한다. 문서를 별도로 관리하면 코드와 싱크가 맞지 않는다.
→ **springdoc-openapi (Swagger UI)** 로 코드에서 API 문서 자동 생성

---

## 아키텍처

### 연동 구조

파트너사 서버는 `X-Api-Key`로 채팅방을 생성하고, 단기 세션 토큰을 자사 사용자에게 전달한다.
사용자(브라우저)는 세션 토큰으로만 WebSocket에 연결하며, `X-Api-Key`는 브라우저에 전달되지 않는다.

```
[파트너사 서버] ──X-Api-Key──▶ POST /api/v1/chat-rooms  (채팅방 생성 + 세션 토큰 발급)
                                        ↓ sessionToken(JWT) 반환
[파트너사 프론트] ◀─── 토큰 ───[파트너사 서버]
        ↓
[파트너사 프론트] ──JWT──▶ WebSocket /ws  (실시간 채팅)
```

- `X-Api-Key`: 파트너사 서버 환경변수에만 보관, 브라우저 노출 금지
- `sessionToken`: 특정 채팅방·사용자 한정 단기 JWT, 브라우저 전달 가능

### 파트너사 데이터 격리 (멀티테넌시)

여러 파트너사가 하나의 DB를 공유하되, 모든 쿼리에 파트너사 ID 필터가 자동 적용된다.

- **Shared DB / Shared Schema** 전략
- Hibernate 6 `@TenantId` 기반 Row-level 격리
- `X-Api-Key` 헤더 수신 → 파트너사 조회 → 요청 스코프에 파트너사 ID 세팅
- WebSocket: STOMP CONNECT 시 JWT로 인증, 파트너사 ID를 세션에 유지

---

## 로컬 실행

### 1. `application.yml` 생성

`src/main/resources/application.yml` 파일을 직접 생성한다. (보안상 git 제외)

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

### 2. 실행

```bash
./gradlew bootRun
```

---

## 개발 환경 URL

| URL | 설명 |
|-----|------|
| `http://localhost:8080/swagger-ui.html` | API 문서 |
| `http://localhost:8080/h2-console` | H2 DB 콘솔 |
| `http://localhost:8080/test-chat.html` | WebSocket 테스트 페이지 (개발 전용) |

---

## X-Api-Key 보안

### HTTPS 환경에서 헤더 전송이 안전한 이유

HTTP 요청은 크게 세 부분으로 이루어진다: 요청 라인, 헤더, 바디.
HTTPS는 TLS 핸드셰이크 이후 이 세 부분 **모두**를 암호화한다.
즉, `X-Api-Key` 헤더는 네트워크 경로 어디에서도 평문으로 노출되지 않는다.

```
HTTP (비암호화)   →  요청 라인·헤더·바디 전체 평문 노출  →  탈취 가능
HTTPS (TLS 암호화) →  요청 라인·헤더·바디 전체 암호화    →  중간자가 읽을 수 없음
```

Stripe, Twilio, GitHub 등 B2B SaaS가 동일한 방식(`Authorization` 또는 커스텀 헤더)을 사용하는 이유다.

> **전제**: 반드시 `https://` 엔드포인트를 호출해야 한다. `http://`로 호출하면 헤더가 평문으로 전송된다.
현재 SaaS를 https서버 화 시키는 방법은 검토중에 있음. 배포까지 고려해서 Railway/Render/Koyeb 고려중 
---

### 파트너사 시스템에 필요한 것

두 가지만 추가하면 된다.

**① 환경변수에 API 키 저장**

발급받은 `X-Api-Key`를 서버 환경변수에 보관한다. 코드에 직접 작성하거나 프론트엔드에 전달하면 안 된다.

```bash
# .env 또는 서버 환경변수
CHATPAY_API_KEY=발급받은_키
```

**② API 호출 시 헤더 추가**

채팅방 생성 등 서버 to 서버 API 호출 시 헤더에 포함한다.

```java
// Spring (RestClient)
restClient.post()
    .uri("https://api.chatpay.com/api/v1/chat-rooms")
    .header("X-Api-Key", System.getenv("CHATPAY_API_KEY"))
    .body(request)
    .retrieve()
    .toEntity(ChatRoomResponse.class);
```

```javascript
// Node.js (axios)
await axios.post('https://api.chatpay.com/api/v1/chat-rooms', body, {
  headers: { 'X-Api-Key': process.env.CHATPAY_API_KEY }
});
```

프론트엔드(브라우저)에서는 이 API를 직접 호출하지 않는다. 파트너사 서버가 중계하고, 브라우저에는 단기 `sessionToken`만 전달된다.

---

## 파트너사 연동 흐름

### Step 1. 채팅방 생성 및 세션 토큰 발급

파트너사 **서버**에서 호출한다. (`X-Api-Key`는 서버에서만 사용, 브라우저 노출 금지)

```http
POST /api/v1/chat-rooms
X-Api-Key: {파트너사_API_키}
Content-Type: application/json

{
  "externalUserId": "파트너사_시스템의_유저_PK",
  "externalItemId": "파트너사_시스템의_상품_PK",
  "itemName": "상품명",
  "itemPrice": 20000
}
```

응답:
```json
{
  "chatRoomId": 1,
  "sessionToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

### Step 2. WebSocket 연결

파트너사 프론트에서 수령한 `sessionToken`으로 연결한다.

```javascript
const client = new StompJs.Client({
  brokerURL: 'ws://localhost:8080/ws',
  connectHeaders: { Authorization: 'Bearer {sessionToken}' }
});
```

### Step 3. 메시지 전송

```javascript
client.publish({
  destination: `/app/chat/{chatRoomId}`,
  body: JSON.stringify({ content: '안녕하세요', messageType: 'TEXT' })
});
```

수신 구독:
```javascript
client.subscribe(`/topic/chat/{chatRoomId}`, (message) => {
  console.log(JSON.parse(message.body));
});
```

---

## 테스트 시나리오

개발용 더미 데이터(`data.sql`)에 두 가지 시나리오가 준비되어 있다.

| 시나리오 | externalUserId | 설명 |
|---------|---------------|------|
| 신규 사용자 | `user-001` | 지갑·채팅 이력 없음. API 호출 시 사용자+지갑 신규 생성 |
| 기존 사용자 | `user-002` | 지갑(잔액 50,000원) + 과거 채팅 메시지 50개 보유 |

개발용 API 키: `test-api-key-0000000000001`
