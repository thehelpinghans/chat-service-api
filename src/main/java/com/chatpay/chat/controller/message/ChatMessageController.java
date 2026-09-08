package com.chatpay.chat.controller.message;

import com.chatpay.chat.dto.message.FindMessagesAfterResponse;
import com.chatpay.chat.dto.message.FindMessagesResponse;
import com.chatpay.chat.dto.message.SendMessageResponse;
import com.chatpay.common.broadcast.ChatRoomBroadcaster;
import com.chatpay.common.multitenancy.TenantFilter;
import com.chatpay.chat.dto.message.ChatMessageRequest;
import com.chatpay.chat.service.message.create.ChatMessageCreateService;
import com.chatpay.chat.service.message.find.ChatMessageFindService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user/chat-rooms")
@RequiredArgsConstructor
@Tag(name = "Chat Message", description = "채팅 메시지 전송/조회 API")
public class ChatMessageController {

    private final ChatMessageCreateService chatMessageCreateService;
    private final ChatMessageFindService chatMessageFindService;
    private final ChatRoomBroadcaster chatRoomBroadcaster;

    @PostMapping("/{chatRoomId}/messages")
    @Operation(summary = "채팅 메시지 전송")
    public ResponseEntity<? extends SendMessageResponse> sendMessage(
            @PathVariable Long chatRoomId,
            @RequestBody @Valid ChatMessageRequest request,
            @RequestAttribute(value = TenantFilter.CHAT_ROOM_ATTRIBUTE, required = false) Long tokenChatRoomId,
            @RequestAttribute(value = TenantFilter.USER_ATTRIBUTE, required = false) Long userId) {

        SendMessageResponse response = chatMessageCreateService.createMessage(chatRoomId, tokenChatRoomId, userId, request);

        if (response instanceof SendMessageResponse.Sent success) {
            chatRoomBroadcaster.send(chatRoomId, success);
        }

        return switch (response) {
            case SendMessageResponse.Sent r -> ResponseEntity.ok(r);

            case SendMessageResponse.ChatRoomAccessDenied r -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(r);

            case SendMessageResponse.ChatRoomNotFound r -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(r);

            case SendMessageResponse.UserNotFound r -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(r);

            case SendMessageResponse.UserSuspended r -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(r);
        };
    }

    @GetMapping("/{chatRoomId}/messages")
    @Operation(summary = "채팅 메시지 목록 조회")
    public ResponseEntity<? extends FindMessagesResponse> getMessages(
            @PathVariable Long chatRoomId,
            @RequestAttribute(value = TenantFilter.CHAT_ROOM_ATTRIBUTE, required = false) Long tokenChatRoomId,
            @RequestParam(required = false) Long lastMessageId,
            @RequestParam(defaultValue = "20") int size) {

        FindMessagesResponse response = chatMessageFindService.findMessages(chatRoomId, tokenChatRoomId, lastMessageId, size);

        return switch (response) {

            case FindMessagesResponse.Found r -> ResponseEntity.ok(r);

            case FindMessagesResponse.ChatRoomAccessDenied r -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(r);
        };
    }

    @GetMapping("/{chatRoomId}/messages/sync")
    @Operation(summary = "재연결 시 메시지 캐치업 조회")
    public ResponseEntity<? extends FindMessagesAfterResponse> syncMessages(
            @PathVariable Long chatRoomId,
            @RequestAttribute(value = TenantFilter.CHAT_ROOM_ATTRIBUTE, required = false) Long tokenChatRoomId,
            @RequestParam Long afterMessageId,
            @RequestParam(defaultValue = "20") int size) {

        FindMessagesAfterResponse response = chatMessageFindService.findMessagesAfter(chatRoomId, tokenChatRoomId, afterMessageId, size);

        return switch (response) {
            case FindMessagesAfterResponse.Found r -> ResponseEntity.ok(r);

            case FindMessagesAfterResponse.ChatRoomAccessDenied r -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(r);
        };
    }
}
