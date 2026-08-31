package com.chatpay.chat.controller.token;

import com.chatpay.chat.dto.token.ChatRoomTokenResponse;
import com.chatpay.chat.dto.token.IssueTenantTokenResponse;
import com.chatpay.chat.dto.token.IssueUserTokenResponse;
import com.chatpay.chat.dto.token.TokenIssueRequest;
import com.chatpay.chat.service.token.ChatRoomTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenant/chat-rooms")
@RequiredArgsConstructor
@Tag(name = "Chat Room Token", description = "채팅방 세션 토큰 발급 API")
public class ChatRoomTokenController {

    private final ChatRoomTokenService chatRoomTokenService;

    @PostMapping("/{chatRoomId}/tokens")
    @Operation(summary = "구매자 세션 토큰 발급")
    public ResponseEntity<? extends IssueUserTokenResponse> issueUserToken(
            @PathVariable Long chatRoomId,
            @RequestBody TokenIssueRequest request) {

        IssueUserTokenResponse response = chatRoomTokenService.issueUserToken(chatRoomId, request.externalUserId());

        return switch (response) {
            case IssueUserTokenResponse.Issued r -> ResponseEntity.ok(r);

            case IssueUserTokenResponse.UserNotFound r -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(r);

            case IssueUserTokenResponse.ChatRoomNotFound r -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(r);

            case IssueUserTokenResponse.ChatRoomAccessDenied r -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(r);
        };
    }

    @PostMapping("/{chatRoomId}/tenant-tokens")
    @Operation(summary = "테넌트(판매자) 세션 토큰 발급")
    public ResponseEntity<? extends IssueTenantTokenResponse> issueTenantToken(@PathVariable Long chatRoomId) {

        IssueTenantTokenResponse response = chatRoomTokenService.issueTenantToken(chatRoomId);

        return switch (response) {
            case IssueTenantTokenResponse.Issued r -> ResponseEntity.ok(r);

            case IssueTenantTokenResponse.ChatRoomNotFound r -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(r);
        };
    }
}
