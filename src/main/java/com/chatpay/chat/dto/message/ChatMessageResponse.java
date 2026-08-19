package com.chatpay.chat.dto.message;

import com.chatpay.chat.domain.MessageType;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long id,
        MessageType messageType,
        String content,
        Long userId,
        LocalDateTime createdAt
) {}
