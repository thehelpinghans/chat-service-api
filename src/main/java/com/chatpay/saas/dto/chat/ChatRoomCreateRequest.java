package com.chatpay.saas.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ChatRoomCreateRequest(

        @NotBlank
        String externalUserId,

        @NotBlank
        String externalItemId,

        @NotBlank
        String itemName,

        @NotNull @Positive
        Long itemPrice
) {}
