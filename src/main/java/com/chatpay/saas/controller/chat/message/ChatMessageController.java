package com.chatpay.saas.controller.chat.message;

import com.chatpay.saas.config.TenantFilter;
import com.chatpay.saas.dto.chat.message.ChatMessageRequest;
import com.chatpay.saas.dto.chat.message.ChatMessageResponse;
import com.chatpay.saas.service.chat.message.ChatMessageService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 구매자 브라우저(우리 SDK/iframe 경유)의 채팅 메시지 전송·조회 엔드포인트. Bearer 전용.
 *
 * 인증: TenantFilter가 사전에 JWT를 검증하고
 *       tenantId → TenantIdentifierResolver(Hibernate 멀티테넌시 자동 적용)
 *       userId   → RequestAttribute(USER_ATTRIBUTE)로 이 컨트롤러에 전달.
 */
@RestController
@RequestMapping("/api/v1/user/chat-rooms")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    /**
     * 메시지 저장 + STOMP 브로드캐스트(ChatMessageService.sendMessage 내부에서 처리).
     *
     * userId: TenantFilter → RequestAttributes → 여기서 수령.
     * required = false: 판매자용 userId=null Bearer 토큰(미구현, 공유 UI 경로)이 들어올 때
     * Spring이 "attribute 없음"과 "값이 null"을 구분 못 해 required=true면 400을 내기 때문.
     */
    @PostMapping("/{chatRoomId}/messages")
    @Operation(summary = "채팅 메시지 전송")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @PathVariable Long chatRoomId,
            @RequestBody @Valid ChatMessageRequest request,
            @RequestAttribute(value = TenantFilter.CHAT_ROOM_ATTRIBUTE, required = false) Long tokenChatRoomId,
            @RequestAttribute(value = TenantFilter.USER_ATTRIBUTE, required = false) Long userId) {
        return ResponseEntity.ok(chatMessageService.sendMessage(chatRoomId, tokenChatRoomId, userId, request));
    }

    @GetMapping("/{chatRoomId}/messages")
    @Operation(summary = "채팅 메시지 목록 조회")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(
            @PathVariable Long chatRoomId,
            @RequestAttribute(value = TenantFilter.CHAT_ROOM_ATTRIBUTE, required = false) Long tokenChatRoomId,
            @RequestParam(required = false) Long lastMessageId,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(chatMessageService.findMessages(chatRoomId, tokenChatRoomId, lastMessageId, size));
    }
}
