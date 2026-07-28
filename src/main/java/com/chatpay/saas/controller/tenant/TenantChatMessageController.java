package com.chatpay.saas.controller.tenant;

import com.chatpay.saas.dto.chat.message.ChatMessageRequest;
import com.chatpay.saas.dto.chat.message.ChatMessageResponse;
import com.chatpay.saas.service.chat.message.ChatMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 테넌트 백엔드가 X-Api-Key로 직접 호출하는 raw 메시지 발신 API. 자체 CS/백오피스 통합용(SDK/iframe 미사용).
@RestController
@RequestMapping("/api/v1/tenant/chat-rooms")
@RequiredArgsConstructor
@Tag(name = "Tenant Raw API", description = "테넌트 자체 CS 통합용 raw API (X-Api-Key)")
public class TenantChatMessageController {

    private final ChatMessageService chatMessageService;

    @PostMapping("/{chatRoomId}/messages")
    @Operation(summary = "테넌트 메시지 발신")
    public ResponseEntity<ChatMessageResponse> sendMessageFromTenant(
            @PathVariable Long chatRoomId,
            @RequestBody @Valid ChatMessageRequest request) {
        return null;
        //return ResponseEntity.ok(chatMessageService.sendMessage(chatRoomId, null, request));
    }
}
