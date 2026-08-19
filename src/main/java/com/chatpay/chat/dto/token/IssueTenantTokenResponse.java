package com.chatpay.chat.dto.token;

public sealed interface IssueTenantTokenResponse {
    record Issued(ChatRoomTokenResponse response) implements IssueTenantTokenResponse {}
    record ChatRoomNotFound() implements IssueTenantTokenResponse {}
}
