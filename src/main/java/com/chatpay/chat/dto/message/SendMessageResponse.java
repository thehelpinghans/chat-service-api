package com.chatpay.chat.dto.message;

public sealed interface SendMessageResponse {
    record Sent(ChatMessageResponse message) implements SendMessageResponse {}
    record ChatRoomAccessDenied(String reason) implements SendMessageResponse {}
    record ChatRoomNotFound(String reason) implements SendMessageResponse {}
    record UserNotFound(String reason) implements SendMessageResponse {}
    record UserSuspended(String reason) implements SendMessageResponse {}
}
