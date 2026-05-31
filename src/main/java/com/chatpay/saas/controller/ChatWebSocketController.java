package com.chatpay.saas.controller;

import com.chatpay.saas.config.TenantIdentifierResolver;
import com.chatpay.saas.dto.chat.ChatMessageRequest;
import com.chatpay.saas.dto.chat.ChatMessageResponse;
import com.chatpay.saas.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;
    private final TenantIdentifierResolver tenantIdentifierResolver;
    private final SimpMessagingTemplate messagingTemplate;
    // @EnableWebSocketMessageBroker가 자동으로 빈 등록 — 별도 설정 불필요

    @MessageMapping("/chat/{chatRoomId}")
    public void handleMessage(@DestinationVariable Long chatRoomId,
                              @Payload ChatMessageRequest request,
                              SimpMessageHeaderAccessor headerAccessor) {
        // 1. 세션에서 tenantId, userId 꺼내기
        Long tenantId = (Long) headerAccessor.getSessionAttributes().get("tenantId");
        Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");

        // 2. ChatMessage DB 저장
        try {
            tenantIdentifierResolver.setTenantId(tenantId);
            ChatMessageResponse response = chatService.saveMessage(chatRoomId, userId, request);
            // 3. 브로드캐스트
            messagingTemplate.convertAndSend("/topic/chat/" + chatRoomId, response);
        } finally {
            tenantIdentifierResolver.clear();
        }

    }
}
