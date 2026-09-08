package com.chatpay.chat.service.token.user;

import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.dto.token.ChatRoomTokenResponse;
import com.chatpay.chat.dto.token.IssueUserTokenResponse;
import com.chatpay.chat.service.room.ChatRoomService;
import com.chatpay.common.config.JwtProvider;
import com.chatpay.common.domain.User;
import com.chatpay.common.message.MessageResolver;
import com.chatpay.common.multitenancy.TenantIdentifierResolver;
import com.chatpay.common.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserTokenService {

    private final UserService userService;
    private final ChatRoomService chatRoomService;
    private final JwtProvider jwtProvider;
    private final TenantIdentifierResolver tenantIdentifierResolver;
    private final MessageResolver messages;

    @Transactional(readOnly = true)
    public IssueUserTokenResponse issueUserToken(Long chatRoomId, String externalUserId) {

        User user = userService.findUserByExternalId(externalUserId).orElse(null);
        if (UserTokenValidator.checkUserExists(user, messages)
                instanceof UserTokenValidator.Check.Fail(IssueUserTokenResponse response)) return response;

        ChatRoom chatRoom = chatRoomService.findChatRoomById(chatRoomId).orElse(null);
        if (UserTokenValidator.checkChatRoomExists(chatRoom, messages)
                instanceof UserTokenValidator.Check.Fail(IssueUserTokenResponse response)) return response;

        if (UserTokenValidator.checkOwnership(chatRoom, user, messages)
                instanceof UserTokenValidator.Check.Fail(IssueUserTokenResponse response)) return response;

        Long tenantId = tenantIdentifierResolver.resolveCurrentTenantIdentifier();

        String token = jwtProvider.createToken(chatRoomId, user.getId(), tenantId);

        return new IssueUserTokenResponse.Issued(new ChatRoomTokenResponse(token));
    }
}
