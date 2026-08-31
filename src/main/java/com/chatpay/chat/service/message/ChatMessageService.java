package com.chatpay.chat.service.message;

import com.chatpay.chat.domain.ChatMessage;
import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.domain.MessageType;
import com.chatpay.chat.dto.message.*;
import com.chatpay.chat.repository.ChatMessageRepository;
import com.chatpay.chat.repository.ChatRoomRepository;
import com.chatpay.common.domain.User;
import com.chatpay.common.domain.UserStatus;
import com.chatpay.common.message.MessageResolver;
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
    private final MessageResolver messages;

    @Transactional
    public SendMessageResponse createMessage(Long chatRoomId, Long tokenChatRoomId, Long userId, ChatMessageRequest request) {

        if (!chatRoomId.equals(tokenChatRoomId)) {
            return new SendMessageResponse.ChatRoomAccessDenied(messages.get("chat.access-denied"));
        }

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId).orElse(null);
        if (chatRoom == null) {
            return new SendMessageResponse.ChatRoomNotFound(messages.get("chat.send.chatroom-not-found"));
        }

        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return new SendMessageResponse.UserNotFound(messages.get("chat.send.user-not-found"));
            }
            if (user.getStatus() != UserStatus.ACTIVE) {
                return new SendMessageResponse.UserSuspended(messages.get("chat.send.user-suspended"));
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
            return new FindMessagesResponse.ChatRoomAccessDenied(messages.get("chat.access-denied"));
        }

        List<ChatMessage> chatMessages;
        if (lastMessageId == null) {
            chatMessages = chatMessageRepository.findByChatRoomIdOrderByIdDesc(chatRoomId, Limit.of(size));
        } else {
            chatMessages = chatMessageRepository.findByChatRoomIdAndIdLessThanOrderByIdDesc(chatRoomId, lastMessageId, Limit.of(size));
        }

        List<ChatMessageResponse> responses = chatMessages.stream()
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

    public FindMessagesAfterResponse findMessagesAfter(Long chatRoomId, Long tokenChatRoomId, Long afterMessageId, int size) {

        if (!chatRoomId.equals(tokenChatRoomId)) {
            return new FindMessagesAfterResponse.ChatRoomAccessDenied(messages.get("chat.access-denied"));
        }

        List<ChatMessage> chatMessages = chatMessageRepository.findByChatRoomIdAndIdGreaterThanOrderByIdAsc(chatRoomId, afterMessageId, Limit.of(size));

        List<ChatMessageResponse> responses = chatMessages.stream()
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

        return new FindMessagesAfterResponse.Found(responses);
    }

    public ChatMessage createPaymentRequestMessage(ChatRoom chatRoom) {
        return chatMessageRepository.save(ChatMessage.create(chatRoom, null, "お支払いリクエストが届きました", MessageType.PAYMENT_REQUEST));
    }

    public Optional<ChatMessage> findChatMessageById(Long chatMessageId) {
        return chatMessageRepository.findById(chatMessageId);
    }

}
