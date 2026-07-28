package com.chatpay.saas.controller;

import com.chatpay.saas.config.TenantFilter;
import com.chatpay.saas.dto.chat.chatmessage.ChatMessageRequest;
import com.chatpay.saas.dto.chat.chatmessage.ChatMessageResponse;
import com.chatpay.saas.service.chat.ChatMessageService;
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
@RequestMapping("/api/v1/chat-rooms")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    /**
     * 메시지 저장 + STOMP 브로드캐스트(ChatMessageService.sendMessage 내부에서 처리).
     *
     * userId: TenantFilter → RequestAttributes → 여기서 수령
     */
    @PostMapping("/{chatRoomId}/messages")
    @Operation(summary = "채팅 메시지 전송")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @PathVariable Long chatRoomId,
            @RequestBody @Valid ChatMessageRequest request,
            @RequestAttribute(TenantFilter.USER_ATTRIBUTE) Long userId) {
        return ResponseEntity.ok(chatMessageService.sendMessage(chatRoomId, userId, request));
    }

    /**
     * 커서 기반 페이지네이션으로 메시지 목록 조회.
     * lastMessageId 없으면 최신 N개, 있으면 해당 id 이전 N개.
     * tenantId는 TenantIdentifierResolver가 Hibernate 쿼리에 자동 적용.
     */
    @GetMapping("/{chatRoomId}/messages")
    @Operation(summary = "채팅 메시지 목록 조회")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(
            @PathVariable Long chatRoomId,
            @RequestParam(required = false) Long lastMessageId,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(chatMessageService.findMessages(chatRoomId, lastMessageId, size));
    }
}
