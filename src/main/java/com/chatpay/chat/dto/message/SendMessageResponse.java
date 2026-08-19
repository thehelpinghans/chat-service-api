package com.chatpay.chat.dto.message;

public sealed interface SendMessageResponse {
    record Sent(ChatMessageResponse message) implements SendMessageResponse {}
    record ChatRoomAccessDenied() implements SendMessageResponse {}
    record UserSuspended() implements SendMessageResponse {}
}
