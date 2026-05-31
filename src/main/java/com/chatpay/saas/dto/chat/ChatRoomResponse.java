package com.chatpay.saas.dto.chat;

public record ChatRoomResponse(
        Long chatRoomId,
        String sessionToken
) {}
