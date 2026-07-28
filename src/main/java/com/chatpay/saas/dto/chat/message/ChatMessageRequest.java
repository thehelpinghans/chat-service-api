package com.chatpay.saas.dto.chat.message;

import com.chatpay.saas.domain.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChatMessageRequest(

        // 메시지 내용 (TEXT 타입이면 텍스트, PAYMENT_REQUEST 타입이면 결제 요청 설명 등)
        @NotBlank
        String content,

        // 메시지 유형: TEXT(일반 채팅) / PAYMENT_REQUEST(결제 요청)
        // ChatMessage 엔티티의 MessageType enum과 매핑
        @NotNull
        MessageType messageType

) {}
