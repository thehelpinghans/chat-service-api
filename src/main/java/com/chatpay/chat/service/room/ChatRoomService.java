package com.chatpay.chat.service.room;

import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.dto.room.ChatRoomCreateRequest;
import com.chatpay.chat.dto.room.ChatRoomResponse;
import com.chatpay.chat.dto.room.ChatRoomUpsertResult;
import com.chatpay.chat.repository.ChatRoomRepository;
import com.chatpay.common.domain.Item;
import com.chatpay.common.domain.User;
import com.chatpay.common.service.ItemService;
import com.chatpay.common.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final UserService userService;
    private final ItemService itemService;
    private final ChatRoomRepository chatRoomRepository;

    @Transactional(readOnly = true)
    public Optional<ChatRoom> findChatRoom(String externalUserId, String externalItemId) {
        return chatRoomRepository.findByUserExternalIdAndItemExternalItemId(externalUserId, externalItemId);
    }

    @Transactional(readOnly = true)
    public Optional<ChatRoom> findChatRoomById(Long chatRoomId) {
        return chatRoomRepository.findById(chatRoomId);
    }

    @Transactional
    public ChatRoomUpsertResult getOrCreateChatRoom(ChatRoomCreateRequest request) {

        User user = userService.getOrCreateUser(request.externalUserId());

        Item item = itemService.getOrCreateItem(request.externalItemId(), request.itemName(), request.itemPrice());

        Optional<ChatRoom> existing = chatRoomRepository.findByUserIdAndItemId(user.getId(), item.getId());
        ChatRoom chatRoom;
        boolean created = existing.isEmpty();
        if (existing.isPresent()) {
            chatRoom = existing.get();
        } else {
            log.info("Creating chat room: userId={}, itemId={}", user.getId(), item.getId());
            chatRoom = chatRoomRepository.save(ChatRoom.create(user, item));
        }
        return new ChatRoomUpsertResult(new ChatRoomResponse(chatRoom.getId()), created);
    }

}