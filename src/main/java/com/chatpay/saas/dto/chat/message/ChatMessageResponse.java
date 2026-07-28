package com.chatpay.saas.dto.chat.chatmessage;

import com.chatpay.saas.domain.MessageType;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long id,
        MessageType messageType,
        String content,
        Long userId,
        LocalDateTime createdAt
) {}
