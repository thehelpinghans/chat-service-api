package com.chatpay.chat.dto.token;

import jakarta.validation.constraints.NotBlank;

public record TokenIssueRequest(
        @NotBlank
        String externalUserId
) {}
