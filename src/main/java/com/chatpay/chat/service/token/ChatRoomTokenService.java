package com.chatpay.chat.service.token;

import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.dto.token.ChatRoomTokenResponse;
import com.chatpay.chat.dto.token.IssueTenantTokenResponse;
import com.chatpay.chat.dto.token.IssueUserTokenResponse;
import com.chatpay.chat.service.room.ChatRoomService;
import com.chatpay.common.config.JwtProvider;
import com.chatpay.common.config.TenantIdentifierResolver;
import com.chatpay.common.domain.User;
import com.chatpay.common.message.MessageResolver;
import com.chatpay.common.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatRoomTokenService {

    private final UserService userService;
    private final ChatRoomService chatRoomService;
    private final JwtProvider jwtProvider;
    private final TenantIdentifierResolver tenantIdentifierResolver;
    private final MessageResolver messages;

    public IssueUserTokenResponse issueUserToken(Long chatRoomId, String externalUserId) {

        User user = userService.findUserByExternalId(externalUserId).orElse(null);
        if (user == null) {
            return new IssueUserTokenResponse.UserNotFound(messages.get("chat.token.user-not-found"));
        }

        ChatRoom chatRoom = chatRoomService.findChatRoomById(chatRoomId).orElse(null);
        if (chatRoom == null) {
            return new IssueUserTokenResponse.ChatRoomNotFound(messages.get("chat.token.chatroom-not-found"));
        }

        if (!chatRoom.getUser().getId().equals(user.getId())) {
            return new IssueUserTokenResponse.ChatRoomAccessDenied(messages.get("chat.token.access-denied"));
        }

        Long tenantId = tenantIdentifierResolver.resolveCurrentTenantIdentifier();

        String token = jwtProvider.createToken(chatRoomId, user.getId(), tenantId);

        return new IssueUserTokenResponse.Issued(new ChatRoomTokenResponse(token));
    }

    public IssueTenantTokenResponse issueTenantToken(Long chatRoomId) {

        ChatRoom chatRoom = chatRoomService.findChatRoomById(chatRoomId).orElse(null);
        if (chatRoom == null) {
            return new IssueTenantTokenResponse.ChatRoomNotFound(messages.get("chat.token.chatroom-not-found"));
        }

        Long tenantId = tenantIdentifierResolver.resolveCurrentTenantIdentifier();

        String token = jwtProvider.createToken(chatRoomId, null, tenantId);

        return new IssueTenantTokenResponse.Issued(new ChatRoomTokenResponse(token));
    }
}
