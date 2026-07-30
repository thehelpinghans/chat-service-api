package com.chatpay.saas.service.chat.message;

import com.chatpay.saas.domain.*;
import com.chatpay.saas.dto.chat.message.ChatMessageRequest;
import com.chatpay.saas.dto.chat.message.ChatMessageResponse;
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

    // 저장 + STOMP 브로드캐스트를 한 단위로 묶은 진입점. Bearer(구매자/판매자) 세션 전용 —
    // 테넌트 백오피스의 결제 요청 생성(X-Api-Key)은 별도 TradeService에서 처리(controller.trade.TradeController).
    @Transactional
    public ChatMessageResponse sendMessage(Long chatRoomId, Long tokenChatRoomId, Long userId, ChatMessageRequest request) {

        if (!chatRoomId.equals(tokenChatRoomId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "유효한 세션이 아닙니다.");
        }

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
                ChatMessage.create(chatRoom, user, request.content(), MessageType.TEXT));

        return new ChatMessageResponse(
                saved.getId(), saved.getMessageType(), saved.getContent(),
                userId, saved.getCreatedAt());
    }

    public List<ChatMessageResponse> findMessages(Long chatRoomId, Long tokenChatRoomId, Long lastMessageId, int size) {

        if (!chatRoomId.equals(tokenChatRoomId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "유효한 세션이 아닙니다.");
        }

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

    public ChatMessage createPaymentRequestMessage(ChatRoom chatRoom) {
        return chatMessageRepository.save(ChatMessage.create(chatRoom, null, "결제 요청 드립니다", MessageType.PAYMENT_REQUEST));
    }
}
