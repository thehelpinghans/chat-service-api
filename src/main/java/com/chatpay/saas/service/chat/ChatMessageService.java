package com.chatpay.saas.service.chat;

import com.chatpay.saas.domain.ChatMessage;
import com.chatpay.saas.domain.ChatRoom;
import com.chatpay.saas.domain.User;
import com.chatpay.saas.domain.UserStatus;
import com.chatpay.saas.dto.chat.ChatMessageRequest;
import com.chatpay.saas.dto.chat.ChatMessageResponse;
import com.chatpay.saas.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;

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

        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
            if (user.getStatus() != UserStatus.ACTIVE) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "정지 또는 차단된 사용자입니다.");
            }
        }

        ChatMessage saved = chatMessageRepository.save(
                ChatMessage.create(chatRoom, user, request.content(), request.messageType()));

        return new ChatMessageResponse(
                saved.getId(), saved.getMessageType(), saved.getContent(),
                userId, saved.getCreatedAt());
    }

}
