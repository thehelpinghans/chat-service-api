package com.chatpay.saas.controller;

import com.chatpay.saas.dto.chat.chatroom.ChatRoomCreateRequest;
import com.chatpay.saas.dto.chat.chatroom.ChatRoomLookupResponse;
import com.chatpay.saas.dto.chat.chatroom.ChatRoomResponse;
import com.chatpay.saas.dto.chat.chatroom.ChatRoomUpsertResult;
import com.chatpay.saas.service.chat.ChatRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/chat-rooms")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "채팅 API")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    // 고객사 백엔드에서 호출하는 API(클라이언트 호출 금지)
    @GetMapping("/{externalUserId}/{externalItemId}")
    @Operation(summary = "채팅방 조회")
    public ResponseEntity<ChatRoomLookupResponse> findChatRoom (@PathVariable String externalUserId, @PathVariable String externalItemId) {
        return chatRoomService.findChatRoom(externalUserId, externalItemId)
                .map(chatRoom -> ResponseEntity.ok(new ChatRoomLookupResponse(chatRoom.getId())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // GlobalExceptionHandler가 에서 409 반환시, 같은 PUT을 처음부터 재호출해야 함(1회 재시도로 확정됨)
    @PutMapping("/{externalUserId}/{externalItemId}")
    @Operation(summary = "채팅방 생성")
    public ResponseEntity<ChatRoomResponse> createChatRoom(@RequestBody ChatRoomCreateRequest request) {

        // TODO: 이 프로젝트에서 Location 헤더까지 챙길지는 판단이 필요해 보입니다
        // GET /api/v1/chat-rooms/{externalUserId}/{externalItemId}(findChatRoom)가 이미 조회용 엔드포인트로 있으니, 201 응답에 그 URI를 Location으로 넣는 것도 검토
        ChatRoomUpsertResult result = chatRoomService.createChatRoom(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.body());
    }

    // 3. 토큰 발급 — POST /api/v1/chat-rooms/{chatRoomId}/tokens
    //    - body: { externalUserId }
    //    - 세션 발급 액션(OAuth2 /token 엔드포인트와 같은 관례) — 리소스 CRUD 아님
    //    - 멱등할 필요 없음(매번 새 토큰 발급해도 무방 — 여러 유효 토큰 동시 존재 OK)
    //    - 201: { token }

    // 서비스 레이어는 Domain Service(ChatRoomService: User/Item 이미 받은 상태에서 ChatRoom 존재만 보장)와
    // Application Service(User/Item/ChatRoom/JwtProvider/TenantIdentifierResolver를 조합하는 오케스트레이터)로 분리.
    // 후자가 UserService/ItemService/JwtProvider를 알아도 되는 유일한 곳.
}
