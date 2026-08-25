package com.chatpay.chat.dto.token;

public sealed interface IssueUserTokenResponse {
    record Issued(ChatRoomTokenResponse response) implements IssueUserTokenResponse {}
    record UserNotFound(String reason) implements IssueUserTokenResponse {}
    record ChatRoomNotFound(String reason) implements IssueUserTokenResponse {}
    record ChatRoomAccessDenied(String reason) implements IssueUserTokenResponse {}
}
