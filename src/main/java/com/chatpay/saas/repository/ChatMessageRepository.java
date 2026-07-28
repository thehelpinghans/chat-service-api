package com.chatpay.saas.repository;

import com.chatpay.saas.domain.ChatMessage;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByChatRoomIdOrderByIdDesc(Long chatRoomId, Limit limit);

    List<ChatMessage> findByChatRoomIdAndIdLessThanOrderByIdDesc(Long chatRoomId, Long lastMessageId, Limit limit);
}
