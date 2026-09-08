package com.chatpay.chat.service.message.create;

import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.dto.message.SendMessageResponse;
import com.chatpay.common.domain.User;
import com.chatpay.common.domain.UserStatus;
import com.chatpay.common.message.MessageResolver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class ChatMessageCreateValidator {

    sealed interface Check {
        record Pass() implements Check {}
        record Fail(SendMessageResponse response) implements Check {}
    }

    /**
     * @return 通過時はPass。chatRoomIdがトークンのchatRoomIdと異なる場合はFail(ChatRoomAccessDenied)。
     */
    static Check checkChatRoomAccess(Long chatRoomId, Long tokenChatRoomId, MessageResolver messages) {
        log.debug("Validating chatRoomId: chatRoomId={}, tokenChatRoomId={}", chatRoomId, tokenChatRoomId);
        if (!chatRoomId.equals(tokenChatRoomId)) {
            return new Check.Fail(new SendMessageResponse.ChatRoomAccessDenied(messages.get("chat.access-denied")));
        }
        return new Check.Pass();
    }

    /**
     * @return 通過時はPass。chatRoomが見つからない場合はFail(ChatRoomNotFound)。
     */
    static Check checkChatRoomExists(ChatRoom chatRoom, MessageResolver messages) {
        log.debug("Validating chatRoom existence: chatRoomId={}", chatRoom == null ? null : chatRoom.getId());
        if (chatRoom == null) {
            return new Check.Fail(new SendMessageResponse.ChatRoomNotFound(messages.get("chat.send.chatroom-not-found")));
        }
        return new Check.Pass();
    }

    /**
     * @return 通過時はPass。userが見つからない場合はFail(UserNotFound)、ACTIVEでない場合はFail(UserSuspended)。
     */
    static Check checkActiveUser(User user, MessageResolver messages) {
        log.debug("Validating active user: userId={}", user == null ? null : user.getId());
        if (user == null) {
            return new Check.Fail(new SendMessageResponse.UserNotFound(messages.get("chat.send.user-not-found")));
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            return new Check.Fail(new SendMessageResponse.UserSuspended(messages.get("chat.send.user-suspended")));
        }
        return new Check.Pass();
    }
}
