package com.chatpay.chat.service.message.find;

import com.chatpay.chat.domain.ChatMessage;
import com.chatpay.chat.dto.message.ChatMessageResponse;
import com.chatpay.chat.dto.message.FindMessagesAfterResponse;
import com.chatpay.chat.dto.message.FindMessagesResponse;
import com.chatpay.chat.repository.ChatMessageRepository;
import com.chatpay.common.domain.User;
import com.chatpay.common.message.MessageResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatMessageFindService {

    private final ChatMessageRepository chatMessageRepository;
    private final MessageResolver messages;

    @Transactional(readOnly = true)
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

        return new FindMessagesResponse.Found(toResponses(chatMessages));
    }

    @Transactional(readOnly = true)
    public FindMessagesAfterResponse findMessagesAfter(Long chatRoomId, Long tokenChatRoomId, Long afterMessageId, int size) {

        if (!chatRoomId.equals(tokenChatRoomId)) {
            return new FindMessagesAfterResponse.ChatRoomAccessDenied(messages.get("chat.access-denied"));
        }

        List<ChatMessage> chatMessages = chatMessageRepository.findByChatRoomIdAndIdGreaterThanOrderByIdAsc(chatRoomId, afterMessageId, Limit.of(size));

        return new FindMessagesAfterResponse.Found(toResponses(chatMessages));
    }

    @Transactional(readOnly = true)
    public Optional<ChatMessage> findChatMessageById(Long chatMessageId) {
        return chatMessageRepository.findById(chatMessageId);
    }

    private List<ChatMessageResponse> toResponses(List<ChatMessage> chatMessages) {
        return chatMessages.stream()
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
}
