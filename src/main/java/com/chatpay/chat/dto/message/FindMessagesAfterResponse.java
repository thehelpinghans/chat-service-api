package com.chatpay.chat.dto.message;

import java.util.List;

public sealed interface FindMessagesAfterResponse {
    record Found(List<ChatMessageResponse> messageList) implements FindMessagesAfterResponse {}
    record ChatRoomAccessDenied(String reason) implements FindMessagesAfterResponse {}
}
