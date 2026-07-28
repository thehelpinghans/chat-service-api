package com.chatpay.saas.service.chat;

import com.chatpay.saas.domain.*;
import com.chatpay.saas.dto.chat.chatroom.ChatRoomCreateRequest;
import com.chatpay.saas.dto.chat.chatroom.ChatRoomResponse;
import com.chatpay.saas.dto.chat.chatroom.ChatRoomUpsertResult;
import com.chatpay.saas.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final UserService userService;
    private final ItemService itemService;
    private final ChatRoomRepository chatRoomRepository;

    public Optional<ChatRoom> findChatRoom(String externalUserId, String externalItemId) {
        return chatRoomRepository.findByUserExternalIdAndItemExternalItemId(externalUserId, externalItemId);
    }

    @Transactional
    public ChatRoomUpsertResult createChatRoom(ChatRoomCreateRequest request) {
        // user/item 단계에서 DataIntegrityViolationException이 나면 여기서 안 잡고 그대로 위(GlobalExceptionHandler)로 전파
        User user = userService.getOrCreateUser(request.externalUserId());
        Item item = itemService.getOrCreateItem(request.externalItemId(), request.itemName(), request.itemPrice());

        Optional<ChatRoom> existing = chatRoomRepository.findByUserIdAndItemId(user.getId(), item.getId());
        ChatRoom chatRoom;
        boolean created = existing.isEmpty();
        if (existing.isPresent()) {
            chatRoom = existing.get();
        } else {
            chatRoom = chatRoomRepository.save(ChatRoom.create(user, item));
        }
        return new ChatRoomUpsertResult(new ChatRoomResponse(chatRoom.getId()), created);
    }

}