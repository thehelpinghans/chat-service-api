package com.chatpay.chat.service.token.tenant;

import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.dto.token.ChatRoomTokenResponse;
import com.chatpay.chat.dto.token.IssueTenantTokenResponse;
import com.chatpay.chat.service.room.ChatRoomService;
import com.chatpay.common.config.JwtProvider;
import com.chatpay.common.message.MessageResolver;
import com.chatpay.common.multitenancy.TenantIdentifierResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantTokenService {

    private final ChatRoomService chatRoomService;
    private final JwtProvider jwtProvider;
    private final TenantIdentifierResolver tenantIdentifierResolver;
    private final MessageResolver messages;

    @Transactional(readOnly = true)
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
