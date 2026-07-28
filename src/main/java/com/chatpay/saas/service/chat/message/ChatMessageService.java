package com.chatpay.saas.service.chat;

import com.chatpay.saas.domain.ChatMessage;
import com.chatpay.saas.domain.ChatRoom;
import com.chatpay.saas.domain.User;
import com.chatpay.saas.domain.UserStatus;
import com.chatpay.saas.dto.chat.chatmessage.ChatMessageRequest;
import com.chatpay.saas.dto.chat.chatmessage.ChatMessageResponse;
import com.chatpay.saas.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private final SimpMessagingTemplate messagingTemplate;

    public List<ChatMessageResponse> findMessages(Long chatRoomId, Long lastMessageId, int size) {

        List<ChatMessage> messages;
        if (lastMessageId == null) {
            messages = chatMessageRepository.findByChatRoomIdOrderByIdDesc(chatRoomId, Limit.of(size));
        } else {
            messages = chatMessageRepository.findByChatRoomIdAndIdLessThanOrderByIdDesc(chatRoomId, lastMessageId, Limit.of(size));
        }

        return messages.stream()
                .map(m -> {
                    User user = m.getUser();
                    return new ChatMessageResponse(
                            m.getId(),
                            m.getMessageType(),
                            m.getContent(),
                            user != null ? user.getId() : null,
                            m.getCreatedAt());
                })
                .toList();
    }

    // 저장 + STOMP 브로드캐스트를 한 단위로 묶은 진입점. Bearer(구매자)/X-Api-Key raw API(테넌트) 양쪽 다 이 메서드만 호출한다.
    @Transactional
    public ChatMessageResponse sendMessage(Long chatRoomId, Long userId, ChatMessageRequest request) {
        ChatMessageResponse response = saveMessage(chatRoomId, userId, request);
        messagingTemplate.convertAndSend("/topic/chat/" + chatRoomId, response);
        return response;
    }

    private ChatMessageResponse saveMessage(Long chatRoomId, Long userId, ChatMessageRequest request) {

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
