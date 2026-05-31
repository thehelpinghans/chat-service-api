# Chat Payment SaaS

C2C(Consumer-to-Consumer) 채팅 기반 결제 SaaS 플랫폼.  
테넌트(파트너사)가 자신의 서비스에 임베드하여 사용자 간 채팅·거래·결제 흐름을 제공한다.

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| Language | Java 25 (Virtual Threads) |
| Framework | Spring Boot 3.5.9 |
| Build | Gradle 8.14 |
| ORM | Spring Data JPA + QueryDSL 5.0.0 |
| Realtime | Spring WebSocket (STOMP) |
| DB (dev) | H2 in-memory (MySQL compatibility mode) |
| Docs | springdoc-openapi 2.8.9 |

---

## 아키텍처

### 임베디드 SaaS 연동 구조

```
[테넌트 서버] ──X-Api-Key──▶ POST /api/v1/chats
                                  ↓ sessionToken(JWT) 반환
[테넌트 프론트] ◀─── 토큰 ───[테넌트 서버]
       ↓
[테넌트 프론트] ──JWT──▶ WebSocket /ws
```

- `X-Api-Key`: 테넌트 서버 환경변수에만 보관, 브라우저 노출 금지
- `sessionToken`: 특정 채팅방+유저 한정 단기 JWT, 브라우저 전달 가능

### 멀티테넌시

- **Shared DB / Shared Schema** 전략
- `@TenantId` (Hibernate 6) 기반 Row-level 격리
- `TenantFilter`에서 `X-Api-Key` → 테넌트 조회 → 요청 스코프에 tenantId 세팅
- WebSocket은 STOMP CONNECT 시 JWT로 인증, ChannelInterceptor에서 ThreadLocal 세팅

---

## 테넌트 연동 흐름

### Step 1. 채팅방 생성 및 세션 토큰 발급

테넌트 서버에서 호출한다.

```http
POST /api/v1/chats
X-Api-Key: {테넌트_API_키}
Content-Type: application/json

{
  "externalUserId": "테넌트_시스템의_유저_PK",
  "externalItemId": "테넌트_시스템의_상품_PK",
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

테넌트 프론트에서 수령한 `sessionToken`으로 연결한다.

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
