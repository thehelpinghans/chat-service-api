package com.chatpay.saas.controller;

import com.chatpay.saas.dto.chat.ChatMessageResponse;
import com.chatpay.saas.dto.chat.ChatRoomCreateRequest;
import com.chatpay.saas.dto.chat.ChatRoomResponse;
import com.chatpay.saas.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chats")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "채팅 API")
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    @Operation(summary = "채팅방 생성 및 세션 토큰 발급")
    //테넌트 시스템의 유저/상품 PK를 받아야함, 테넌트마다 PK 형식이 다를 수 있어서(Long 정수, UUID, 복합키 문자열 등) string으로 선언
    public ResponseEntity<ChatRoomResponse> createChatRoom(@RequestBody @Valid ChatRoomCreateRequest request) {
        ChatRoomResponse response = chatService.createOrGetChatRoom(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
        //GlobalExceptionHandler 구현 필요(전역 예외, 실패 처리)
    }

    @GetMapping("/{chatRoomId}/messages")
    @Operation(summary = "채팅 메시지 목록 조회")
    public ResponseEntity<List<ChatMessageResponse>> getChatMessages(
            @PathVariable Long chatRoomId,
            @RequestParam(required = false) Long lastMessageId,
            @RequestParam(defaultValue = "20") int size) {
        List<ChatMessageResponse> messages = chatService.findMessages(chatRoomId, lastMessageId, size);
        return ResponseEntity.ok(messages);
    }
}
