package com.chatpay.chat.repository;

import com.chatpay.chat.domain.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByUserIdAndItemId(Long userId, Long itemId);

    Optional<ChatRoom> findByUserExternalIdAndItemExternalItemId(String externalUserId, String externalItemId);
}
