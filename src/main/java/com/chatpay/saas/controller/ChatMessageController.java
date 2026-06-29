package com.chatpay.saas.controller;

import com.chatpay.saas.config.TenantFilter;
import com.chatpay.saas.dto.chat.ChatMessageRequest;
import com.chatpay.saas.dto.chat.ChatMessageResponse;
import com.chatpay.saas.service.chat.ChatMessageService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 구매자 브라우저의 채팅 메시지 전송·조회 엔드포인트.
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
    private final SimpMessagingTemplate messagingTemplate; // @EnableWebSocketMessageBroker가 자동 빈 등록

    /**
     * 메시지 저장 후 해당 채팅방 구독자 전체에게 STOMP 브로드캐스트.
     *
     * userId: TenantFilter → RequestAttributes → 여기서 수령
     * 저장 결과: ChatMessageService → ChatMessageResponse
     * 브로드캐스트: /topic/chat/{chatRoomId} 구독 중인 판매자·구매자 브라우저로 전달
     */
    @PostMapping("/{chatRoomId}/messages")
    @Operation(summary = "채팅 메시지 전송")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @PathVariable Long chatRoomId,
            @RequestBody @Valid ChatMessageRequest request,
            @RequestAttribute(TenantFilter.USER_ATTRIBUTE) Long userId) {
        ChatMessageResponse response = chatMessageService.saveMessage(chatRoomId, userId, request);
        messagingTemplate.convertAndSend("/topic/chat/" + chatRoomId, response);
        return ResponseEntity.ok(response);
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
