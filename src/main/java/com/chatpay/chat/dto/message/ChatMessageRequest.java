package com.chatpay.chat.dto.message;

import jakarta.validation.constraints.NotBlank;

public record ChatMessageRequest(
        @NotBlank
        String content
) {}
