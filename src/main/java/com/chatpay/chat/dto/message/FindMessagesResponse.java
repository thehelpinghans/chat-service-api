package com.chatpay.chat.dto.message;

import java.util.List;

public sealed interface FindMessagesResponse {
    record Found(List<ChatMessageResponse> messageList) implements FindMessagesResponse {}
    record ChatRoomAccessDenied() implements FindMessagesResponse {}
}
