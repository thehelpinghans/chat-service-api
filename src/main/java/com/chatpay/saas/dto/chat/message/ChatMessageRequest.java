package com.chatpay.saas.dto.chat.message;

import jakarta.validation.constraints.NotBlank;

public record ChatMessageRequest(
        @NotBlank
        String content
) {}
