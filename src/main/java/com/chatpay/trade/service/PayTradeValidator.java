package com.chatpay.trade.service;

import com.chatpay.chat.domain.ChatMessage;
import com.chatpay.chat.domain.MessageType;
import com.chatpay.trade.domain.Trade;
import com.chatpay.trade.domain.TradeStatus;
import com.chatpay.trade.domain.Wallet;
import com.chatpay.trade.dto.PaymentResponse;

import java.util.Objects;

// payTrade 파라미터 검증 전용 — I/O 없는 순수 함수만, 통과/실패를 Check로 표현(null 안 씀)
class PayTradeValidator {

    sealed interface Check {
        record Pass() implements Check {}
        record Fail(PaymentResponse response) implements Check {}
    }

    static Check checkChatRoomAccess(Long chatRoomId, Long tokenChatRoomId) {
        if (!Objects.equals(chatRoomId, tokenChatRoomId)) {
            return new Check.Fail(new PaymentResponse.ChatRoomAccessDenied());
        }
        return new Check.Pass();
    }

    static Check checkPaymentRequestMessage(ChatMessage message, Long chatRoomId) {
        if (message == null) {
            return new Check.Fail(new PaymentResponse.PaymentNotFound());
        }
        if (!Objects.equals(message.getChatRoom().getId(), chatRoomId)) {
            return new Check.Fail(new PaymentResponse.ChatRoomAccessDenied());
        }
        if (message.getMessageType() != MessageType.PAYMENT_REQUEST) {
            return new Check.Fail(new PaymentResponse.InvalidRequestType());
        }
        return new Check.Pass();
    }

    static Check checkPayableTrade(Trade trade, Long userId) {
        if (trade == null) {
            return new Check.Fail(new PaymentResponse.PaymentNotFound());
        }
        if (!trade.getUser().getId().equals(userId)) {
            return new Check.Fail(new PaymentResponse.PaymentOwnerMismatch());
        }
        if (trade.getTradeStatus() != TradeStatus.PENDING) {
            return new Check.Fail(new PaymentResponse.PaymentAlreadyProcessed());
        }
        return new Check.Pass();
    }

    static Check checkSufficientWallet(Wallet wallet, long amount) {
        if (wallet == null) {
            return new Check.Fail(new PaymentResponse.WalletNotFound());
        }
        if (wallet.getBalance() < amount) {
            return new Check.Fail(new PaymentResponse.InsufficientBalance());
        }
        return new Check.Pass();
    }
}
