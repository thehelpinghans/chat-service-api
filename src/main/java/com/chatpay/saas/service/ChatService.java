package com.chatpay.saas.service;

import com.chatpay.saas.config.JwtProvider;
import com.chatpay.saas.config.TenantIdentifierResolver;
import com.chatpay.saas.domain.*;
import com.chatpay.saas.dto.chat.ChatMessageRequest;
import com.chatpay.saas.dto.chat.ChatMessageResponse;
import com.chatpay.saas.dto.chat.ChatRoomCreateRequest;
import com.chatpay.saas.dto.chat.ChatRoomResponse;
import com.chatpay.saas.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final ItemRepository itemRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final JwtProvider jwtProvider;
    private final TenantIdentifierResolver tenantIdentifierResolver;

    @Transactional
    public ChatRoomResponse createOrGetChatRoom(ChatRoomCreateRequest request) {
        User user = userRepository.findByExternalId(request.externalUserId())
                .orElseGet(() -> {
                    User newUser = userRepository.save(User.create(request.externalUserId()));
                    walletRepository.save(Wallet.create(newUser));
                    return newUser;
                });

        Optional<Item> existingItem = itemRepository.findByExternalItemId(request.externalItemId());
        Item item;
        if (existingItem.isPresent()) {
            item = existingItem.get();
            item.update(request.itemName(), request.itemPrice());
        } else {
            item = Item.create(request.externalItemId(), request.itemName(), request.itemPrice());
        }
        itemRepository.save(item);

        try {
            ChatRoom chatRoom = chatRoomRepository.findByUserIdAndItemId(user.getId(), item.getId())
                    .orElseGet(() -> chatRoomRepository.save(ChatRoom.create(user, item)));
            Long tenantId = tenantIdentifierResolver.resolveCurrentTenantIdentifier();
            String token = jwtProvider.createToken(chatRoom.getId(), user.getId(), tenantId);
            return new ChatRoomResponse(chatRoom.getId(), token);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 채팅방입니다.");
        }
    }

    public List<ChatMessageResponse> findMessages(Long chatRoomId, Long lastMessageId, int size) {
        /*
         * 1. chatRoomId + lastMessageId로 메시지 목록 조회
         * 2. List<ChatMessageResponse>로 변환 후 반환
         */
        return null;
    }

    @Transactional
    public ChatMessageResponse saveMessage(Long chatRoomId, Long userId, ChatMessageRequest request) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        ChatMessage saved = chatMessageRepository.save(
                ChatMessage.create(chatRoom, user, request.content(), request.messageType()));

        return new ChatMessageResponse(
                saved.getId(), saved.getMessageType(), saved.getContent(),
                userId, saved.getCreatedAt());
    }
}
