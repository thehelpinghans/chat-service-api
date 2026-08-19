package com.chatpay.chat.controller.message;

import com.chatpay.chat.dto.message.FindMessagesResponse;
import com.chatpay.chat.dto.message.SendMessageResponse;
import com.chatpay.common.broadcast.ChatRoomBroadcaster;
import com.chatpay.common.config.TenantFilter;
import com.chatpay.chat.dto.message.ChatMessageRequest;
import com.chatpay.chat.service.message.ChatMessageService;
import com.chatpay.common.message.MessageResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
@Tag(name = "Chat Message", description = "채팅 메시지 전송/조회 API")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;
    private final ChatRoomBroadcaster chatRoomBroadcaster;
    private final MessageResolver messages;

    @PostMapping("/{chatRoomId}/messages")
    @Operation(summary = "채팅 메시지 전송")
    public ResponseEntity<SendMessageResponse.Sent> sendMessage(
            @PathVariable Long chatRoomId,
            @RequestBody @Valid ChatMessageRequest request,
            @RequestAttribute(value = TenantFilter.CHAT_ROOM_ATTRIBUTE, required = false) Long tokenChatRoomId,
            @RequestAttribute(value = TenantFilter.USER_ATTRIBUTE, required = false) Long userId) {

        SendMessageResponse response = chatMessageService.sendMessage(chatRoomId, tokenChatRoomId, userId, request);

        if (response instanceof SendMessageResponse.Sent success) {
            chatRoomBroadcaster.broadcast(chatRoomId, success);
        }

        return switch (response) {
            case SendMessageResponse.Sent r -> ResponseEntity.ok(r);

            case SendMessageResponse.ChatRoomAccessDenied _ -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, messages.get("chat.send.access-denied"));

            case SendMessageResponse.UserSuspended _ -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, messages.get("chat.send.user-suspended"));
        };
    }

    @GetMapping("/{chatRoomId}/messages")
    @Operation(summary = "채팅 메시지 목록 조회")
    public ResponseEntity<FindMessagesResponse.Found> getMessages(
            @PathVariable Long chatRoomId,
            @RequestAttribute(value = TenantFilter.CHAT_ROOM_ATTRIBUTE, required = false) Long tokenChatRoomId,
            @RequestParam(required = false) Long lastMessageId,
            @RequestParam(defaultValue = "20") int size) {

        //TODO 개별+번들 방식으로 TenantFilter 수정 검토, 다른 방법이 있는지 확인 필요, 업계 패턴 조사해야함
        FindMessagesResponse response = chatMessageService.findMessages(chatRoomId, tokenChatRoomId, lastMessageId, size);

        return switch (response) {

            case FindMessagesResponse.Found r -> ResponseEntity.ok(r);

            case FindMessagesResponse.ChatRoomAccessDenied _ -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, messages.get("chat.access-denied"));
        };
    }
}
