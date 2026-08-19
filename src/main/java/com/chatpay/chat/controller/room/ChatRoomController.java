package com.chatpay.chat.controller.room;

import com.chatpay.chat.dto.room.ChatRoomCreateRequest;
import com.chatpay.chat.dto.room.ChatRoomLookupResponse;
import com.chatpay.chat.dto.room.ChatRoomResponse;
import com.chatpay.chat.dto.room.ChatRoomUpsertResult;
import com.chatpay.chat.service.room.ChatRoomService;
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

    // 고객사 백엔드에서 호출하는 API(클라이언트 호출 금지)
    @GetMapping("/{externalUserId}/{externalItemId}")
    @Operation(summary = "채팅방 조회")
    public ResponseEntity<ChatRoomLookupResponse> findChatRoom(@PathVariable String externalUserId, @PathVariable String externalItemId) {

        return chatRoomService.findChatRoom(externalUserId, externalItemId)
                .map(chatRoom -> ResponseEntity.ok(new ChatRoomLookupResponse(chatRoom.getId())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // GlobalExceptionHandler에서 409 반환시, 같은 PUT을 처음부터 재호출해야 함(1회 재시도로 확정됨)
    @PutMapping("/{externalUserId}/{externalItemId}")
    @Operation(summary = "채팅방 생성")
    public ResponseEntity<ChatRoomResponse> createChatRoom(@RequestBody ChatRoomCreateRequest request) {

        ChatRoomUpsertResult result = chatRoomService.createChatRoom(request);

        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;

        return ResponseEntity.status(status).body(result.body());
    }
}
