package com.chatpay.saas.controller.chat.room;

import com.chatpay.saas.dto.chat.room.ChatRoomCreateRequest;
import com.chatpay.saas.dto.chat.room.ChatRoomLookupResponse;
import com.chatpay.saas.dto.chat.room.ChatRoomResponse;
import com.chatpay.saas.dto.chat.room.ChatRoomTokenResponse;
import com.chatpay.saas.dto.chat.room.ChatRoomUpsertResult;
import com.chatpay.saas.dto.chat.room.TokenIssueRequest;
import com.chatpay.saas.service.chat.room.ChatRoomService;
import com.chatpay.saas.service.chat.room.ChatRoomTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/tenant/chat-rooms")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "채팅 API")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final ChatRoomTokenService chatRoomTokenService;

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

        ChatRoomUpsertResult result = chatRoomService.createChatRoom(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.body());
    }

    @PostMapping("/{chatRoomId}/tokens")
    @Operation(summary = "구매자 세션 토큰 발급")
    public ResponseEntity<ChatRoomTokenResponse> issueUserToken(
            @PathVariable Long chatRoomId,
            @RequestBody TokenIssueRequest request) {
        String token = chatRoomTokenService.issueUserToken(chatRoomId, request.externalUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ChatRoomTokenResponse(token));
    }


    @PostMapping("/{chatRoomId}/tenant-tokens")
    @Operation(summary = "테넌트(판매자) 세션 토큰 발급")
    public ResponseEntity<ChatRoomTokenResponse> issueTenantToken(@PathVariable Long chatRoomId) {
        String token = chatRoomTokenService.issueTenantToken(chatRoomId);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ChatRoomTokenResponse(token));
    }
}
