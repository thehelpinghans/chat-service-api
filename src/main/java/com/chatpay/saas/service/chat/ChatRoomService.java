package com.chatpay.saas.service.chat;

import com.chatpay.saas.config.JwtProvider;
import com.chatpay.saas.config.TenantIdentifierResolver;
import com.chatpay.saas.domain.*;

import com.chatpay.saas.dto.chat.ChatRoomCreateRequest;
import com.chatpay.saas.dto.chat.ChatRoomResponse;
import com.chatpay.saas.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomService {

    private final UserService userService;
    private final ItemService itemService;
    private final ChatRoomRepository chatRoomRepository;
    private final JwtProvider jwtProvider;
    private final TenantIdentifierResolver tenantIdentifierResolver;

    @Transactional
    public ChatRoomResponse createOrGetChatRoom(ChatRoomCreateRequest request) {
        User user = userService.getOrCreateUser(request.externalUserId());
        Item item = itemService.getOrCreateItem(request.externalItemId(), request.itemName(), request.itemPrice());

        ChatRoom chatRoom;
        try {
            chatRoom = chatRoomRepository.findByUserIdAndItemId(user.getId(), item.getId())
                    .orElseGet(() -> chatRoomRepository.save(ChatRoom.create(user, item)));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 채팅방입니다.");
        }
        Long tenantId = tenantIdentifierResolver.resolveCurrentTenantIdentifier();
        String token = jwtProvider.createToken(chatRoom.getId(), user.getId(), tenantId);
        return new ChatRoomResponse(chatRoom.getId(), token);
    }

}
