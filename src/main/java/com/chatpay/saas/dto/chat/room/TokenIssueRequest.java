package com.chatpay.saas.dto.chat.room;

import jakarta.validation.constraints.NotBlank;

public record TokenIssueRequest(
        @NotBlank
        String externalUserId
) {}
