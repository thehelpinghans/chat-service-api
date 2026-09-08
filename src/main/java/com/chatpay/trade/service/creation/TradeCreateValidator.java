package com.chatpay.trade.service.creation;

import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.common.message.MessageResolver;
import com.chatpay.trade.dto.TradeCreateResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class TradeCreateValidator {

    sealed interface Check {
        record Pass() implements Check {}
        record Fail(TradeCreateResponse response) implements Check {}
    }

    /**
     * @return 通過時はPass。userIdがnullでない(オペレーター)場合はFail(SellerOnly)。
     */
    static Check checkSellerOnly(Long userId, MessageResolver messages) {
        log.debug("Validating seller-only: userId={}", userId);
        if (userId != null) {
            return new Check.Fail(new TradeCreateResponse.SellerOnly(messages.get("trade.create.seller-only")));
        }
        return new Check.Pass();
    }

    /**
     * @return 通過時はPass。chatRoomIdがトークンのchatRoomIdと異なる場合はFail(ChatRoomAccessDenied)。
     */
    static Check checkChatRoomAccess(Long chatRoomId, Long tokenChatRoomId, MessageResolver messages) {
        log.debug("Validating chatRoomId: chatRoomId={}, tokenChatRoomId={}", chatRoomId, tokenChatRoomId);
        if (!chatRoomId.equals(tokenChatRoomId)) {
            return new Check.Fail(new TradeCreateResponse.ChatRoomAccessDenied(messages.get("trade.create.chatroom-access-denied")));
        }
        return new Check.Pass();
    }

    /**
     * @return 通過時はPass。chatRoomが見つからない場合はFail(ChatRoomNotFound)。
     */
    static Check checkChatRoomExists(ChatRoom chatRoom, MessageResolver messages) {
        log.debug("Validating chatRoom existence: chatRoomId={}", chatRoom == null ? null : chatRoom.getId());
        if (chatRoom == null) {
            return new Check.Fail(new TradeCreateResponse.ChatRoomNotFound(messages.get("trade.create.chatroom-not-found")));
        }
        return new Check.Pass();
    }
}
