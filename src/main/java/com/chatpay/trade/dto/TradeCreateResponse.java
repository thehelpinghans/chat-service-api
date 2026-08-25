package com.chatpay.trade.dto;

import com.chatpay.chat.domain.MessageType;

import java.time.LocalDateTime;

public sealed interface TradeCreateResponse {
    record Created(Long id, MessageType messageType, Long userId, LocalDateTime createdAt) implements TradeCreateResponse {}
    record SellerOnly(String reason) implements TradeCreateResponse {}
    record ChatRoomAccessDenied(String reason) implements TradeCreateResponse {}
    record ChatRoomNotFound(String reason) implements TradeCreateResponse {}
}
