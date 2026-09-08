package com.chatpay.chat.service.message.create;

import com.chatpay.chat.domain.ChatMessage;
import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.domain.MessageType;
import com.chatpay.chat.dto.message.ChatMessageRequest;
import com.chatpay.chat.dto.message.ChatMessageResponse;
import com.chatpay.chat.dto.message.SendMessageResponse;
import com.chatpay.chat.repository.ChatMessageRepository;
import com.chatpay.chat.service.room.ChatRoomService;
import com.chatpay.common.domain.User;
import com.chatpay.common.message.MessageResolver;
import com.chatpay.common.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageCreateService {

    private final UserService userService;
    private final ChatRoomService chatRoomService;
    private final ChatMessageRepository chatMessageRepository;
    private final MessageResolver messages;

    @Transactional
    public SendMessageResponse createMessage(Long chatRoomId, Long tokenChatRoomId, Long userId, ChatMessageRequest request) {

        if (ChatMessageCreateValidator.checkChatRoomAccess(chatRoomId, tokenChatRoomId, messages)
                instanceof ChatMessageCreateValidator.Check.Fail(SendMessageResponse response)) return response;

        ChatRoom chatRoom = chatRoomService.findChatRoomById(chatRoomId).orElse(null);
        if (ChatMessageCreateValidator.checkChatRoomExists(chatRoom, messages)
                instanceof ChatMessageCreateValidator.Check.Fail(SendMessageResponse response)) return response;

        User user = null;
        if (userId != null) {
            user = userService.findUserById(userId).orElse(null);
            if (ChatMessageCreateValidator.checkActiveUser(user, messages)
                    instanceof ChatMessageCreateValidator.Check.Fail(SendMessageResponse response)) return response;
        }

        ChatMessage saved = chatMessageRepository.save(
                ChatMessage.create(chatRoom, user, request.content(), MessageType.TEXT));

        return new SendMessageResponse.Sent(new ChatMessageResponse(
                saved.getId(), saved.getMessageType(), saved.getContent(),
                userId, saved.getCreatedAt()));
    }

    @Transactional
    public ChatMessage createPaymentRequestMessage(ChatRoom chatRoom) {
        log.info("Creating payment request message: chatRoomId={}", chatRoom.getId());
        return chatMessageRepository.save(ChatMessage.create(chatRoom, null, "お支払いリクエストが届きました", MessageType.PAYMENT_REQUEST));
    }
}
