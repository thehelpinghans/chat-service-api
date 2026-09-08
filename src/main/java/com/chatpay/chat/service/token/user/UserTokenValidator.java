package com.chatpay.chat.service.token.user;

import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.dto.token.IssueUserTokenResponse;
import com.chatpay.common.domain.User;
import com.chatpay.common.message.MessageResolver;

class UserTokenValidator {

    sealed interface Check {
        record Pass() implements Check {}
        record Fail(IssueUserTokenResponse response) implements Check {}
    }

    /**
     * @return 通過時はPass。userが見つからない場合はFail(UserNotFound)。
     */
    static Check checkUserExists(User user, MessageResolver messages) {
        if (user == null) {
            return new Check.Fail(new IssueUserTokenResponse.UserNotFound(messages.get("chat.token.user-not-found")));
        }
        return new Check.Pass();
    }

    /**
     * @return 通過時はPass。chatRoomが見つからない場合はFail(ChatRoomNotFound)。
     */
    static Check checkChatRoomExists(ChatRoom chatRoom, MessageResolver messages) {
        if (chatRoom == null) {
            return new Check.Fail(new IssueUserTokenResponse.ChatRoomNotFound(messages.get("chat.token.chatroom-not-found")));
        }
        return new Check.Pass();
    }

    /**
     * @return 通過時はPass。chatRoomの所有者がuserと異なる場合はFail(ChatRoomAccessDenied)。
     */
    static Check checkOwnership(ChatRoom chatRoom, User user, MessageResolver messages) {
        if (!chatRoom.getUser().getId().equals(user.getId())) {
            return new Check.Fail(new IssueUserTokenResponse.ChatRoomAccessDenied(messages.get("chat.token.access-denied")));
        }
        return new Check.Pass();
    }
}
