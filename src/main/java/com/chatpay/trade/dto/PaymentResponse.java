package com.chatpay.trade.dto;

import com.chatpay.trade.domain.TradeStatus;

public sealed interface PaymentResponse {
    record PaymentSuccess(Long chatMessageId, TradeStatus tradeStatus) implements PaymentResponse {}
    record PaymentNotFound() implements PaymentResponse {}
    record ChatRoomAccessDenied() implements PaymentResponse {}
    record InvalidRequestType() implements PaymentResponse {}
    record PaymentOwnerMismatch() implements PaymentResponse {}
    record PaymentAlreadyProcessed() implements PaymentResponse {}
    record WalletNotFound() implements PaymentResponse {}
    record InsufficientBalance() implements PaymentResponse {}
}
