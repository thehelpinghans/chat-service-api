package com.chatpay.chat.dto.token;

public sealed interface IssueUserTokenResponse {
    record Issued(ChatRoomTokenResponse response) implements IssueUserTokenResponse {}
    record UserNotFound() implements IssueUserTokenResponse {}
    record ChatRoomNotFound() implements IssueUserTokenResponse {}
    record ChatRoomAccessDenied() implements IssueUserTokenResponse {}
}
