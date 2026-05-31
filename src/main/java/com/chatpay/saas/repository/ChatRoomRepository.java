package com.chatpay.saas.repository;

import com.chatpay.saas.domain.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByUserIdAndItemId(Long userId, Long itemId);
}
