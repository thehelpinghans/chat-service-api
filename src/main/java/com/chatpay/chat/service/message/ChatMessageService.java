package com.chatpay.chat.service.message;

import com.chatpay.chat.domain.ChatMessage;
import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.domain.MessageType;
import com.chatpay.chat.dto.message.ChatMessageRequest;
import com.chatpay.chat.dto.message.ChatMessageResponse;
import com.chatpay.chat.dto.message.FindMessagesResponse;
import com.chatpay.chat.dto.message.SendMessageResponse;
import com.chatpay.chat.repository.ChatMessageRepository;
import com.chatpay.chat.repository.ChatRoomRepository;
import com.chatpay.common.domain.User;
import com.chatpay.common.domain.UserStatus;
import com.chatpay.common.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public SendMessageResponse sendMessage(Long chatRoomId, Long tokenChatRoomId, Long userId, ChatMessageRequest request) {

        if (!chatRoomId.equals(tokenChatRoomId)) {
            return new SendMessageResponse.ChatRoomAccessDenied();
        }

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalStateException("토큰이 유효한데 채팅방이 존재하지 않음: " + chatRoomId));

        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalStateException("토큰이 유효한데 사용자가 존재하지 않음: " + userId));
            if (user.getStatus() != UserStatus.ACTIVE) {
                return new SendMessageResponse.UserSuspended();
            }
        }

        ChatMessage saved = chatMessageRepository.save(
                ChatMessage.create(chatRoom, user, request.content(), MessageType.TEXT));

        return new SendMessageResponse.Sent(new ChatMessageResponse(
                saved.getId(), saved.getMessageType(), saved.getContent(),
                userId, saved.getCreatedAt()));
    }

    public FindMessagesResponse findMessages(Long chatRoomId, Long tokenChatRoomId, Long lastMessageId, int size) {

        if (!chatRoomId.equals(tokenChatRoomId)) {
            return new FindMessagesResponse.ChatRoomAccessDenied();
        }

        List<ChatMessage> messages;
        if (lastMessageId == null) {
            messages = chatMessageRepository.findByChatRoomIdOrderByIdDesc(chatRoomId, Limit.of(size));
        } else {
            messages = chatMessageRepository.findByChatRoomIdAndIdLessThanOrderByIdDesc(chatRoomId, lastMessageId, Limit.of(size));
        }

        List<ChatMessageResponse> responses = messages.stream()
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

        return new FindMessagesResponse.Found(responses);
    }

    public ChatMessage createPaymentRequestMessage(ChatRoom chatRoom) {
        return chatMessageRepository.save(ChatMessage.create(chatRoom, null, "결제 요청 드립니다", MessageType.PAYMENT_REQUEST));
    }

    public Optional<ChatMessage> findChatMessageById(Long chatMessageId) {
        return chatMessageRepository.findById(chatMessageId);
    }
}
