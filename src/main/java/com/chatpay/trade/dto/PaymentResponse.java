package com.chatpay.trade.dto;

import com.chatpay.trade.domain.TradeStatus;

public sealed interface PaymentResponse {
    record PaymentSuccess(Long chatMessageId, TradeStatus tradeStatus) implements PaymentResponse {}
    record PaymentNotFound(String reason) implements PaymentResponse {}
    record ChatRoomAccessDenied(String reason) implements PaymentResponse {}
    record InvalidRequestType(String reason) implements PaymentResponse {}
    record PaymentOwnerMismatch(String reason) implements PaymentResponse {}
    record PaymentAlreadyProcessed(String reason) implements PaymentResponse {}
    record WalletNotFound(String reason) implements PaymentResponse {}
    record InsufficientBalance(String reason) implements PaymentResponse {}
}
