package com.chatpay.saas.service.chat.room;

import com.chatpay.saas.config.JwtProvider;
import com.chatpay.saas.config.TenantIdentifierResolver;
import com.chatpay.saas.domain.ChatRoom;
import com.chatpay.saas.domain.User;
import com.chatpay.saas.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ChatRoomTokenService {

    private final UserService userService;
    private final ChatRoomService chatRoomService;
    private final JwtProvider jwtProvider;
    private final TenantIdentifierResolver tenantIdentifierResolver;

    // 구매자용 세션 토큰 발급
    public String issueUserToken(Long chatRoomId, String externalUserId) {
        User user = userService.findUserById(externalUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        ChatRoom chatRoom = chatRoomService.findChatRoomById(chatRoomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."));

        if (!chatRoom.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 채팅방에 대한 접근 권한이 없습니다.");
        }

        Long tenantId = tenantIdentifierResolver.resolveCurrentTenantIdentifier();

        return jwtProvider.createToken(chatRoomId, user.getId(), tenantId);
    }

    // 판매자(테넌트)용 userId=null 세션 토큰 발급
    public String issueTenantToken(Long chatRoomId) {
        // (멀티테넌시 필터가 조회 시 tenantId로 자동 격리하므로, 다른 테넌트 소속 chatRoomId면
        //  findChatRoomById 자체가 이미 empty를 리턴함 — 별도 테넌트 소유권 확인은 불필요, 다만 구현 시 재확인할 것
        chatRoomService.findChatRoomById(chatRoomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."));

        Long tenantId = tenantIdentifierResolver.resolveCurrentTenantIdentifier();

        return jwtProvider.createToken(chatRoomId, null, tenantId);
    }
}
