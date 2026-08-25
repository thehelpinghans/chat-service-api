package com.chatpay.trade.service;

import com.chatpay.chat.domain.ChatMessage;
import com.chatpay.chat.domain.MessageType;
import com.chatpay.common.message.MessageResolver;
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

    static Check checkChatRoomAccess(Long chatRoomId, Long tokenChatRoomId, MessageResolver messages) {
        if (!Objects.equals(chatRoomId, tokenChatRoomId)) {
            return new Check.Fail(new PaymentResponse.ChatRoomAccessDenied(messages.get("payment.chatroom-access-denied")));
        }
        return new Check.Pass();
    }

    static Check checkPaymentRequestMessage(ChatMessage message, Long chatRoomId, MessageResolver messages) {
        if (message == null) {
            return new Check.Fail(new PaymentResponse.PaymentNotFound(messages.get("payment.not-found")));
        }
        if (!Objects.equals(message.getChatRoom().getId(), chatRoomId)) {
            return new Check.Fail(new PaymentResponse.ChatRoomAccessDenied(messages.get("payment.chatroom-access-denied")));
        }
        if (message.getMessageType() != MessageType.PAYMENT_REQUEST) {
            return new Check.Fail(new PaymentResponse.InvalidRequestType(messages.get("payment.invalid-request-type")));
        }
        return new Check.Pass();
    }

    static Check checkPayableTrade(Trade trade, Long userId, MessageResolver messages) {
        if (trade == null) {
            return new Check.Fail(new PaymentResponse.PaymentNotFound(messages.get("payment.not-found")));
        }
        if (!trade.getUser().getId().equals(userId)) {
            return new Check.Fail(new PaymentResponse.PaymentOwnerMismatch(messages.get("payment.owner-mismatch")));
        }
        if (trade.getTradeStatus() != TradeStatus.PENDING) {
            return new Check.Fail(new PaymentResponse.PaymentAlreadyProcessed(messages.get("payment.already-processed")));
        }
        return new Check.Pass();
    }

    static Check checkSufficientWallet(Wallet wallet, long amount, MessageResolver messages) {
        if (wallet == null) {
            return new Check.Fail(new PaymentResponse.WalletNotFound(messages.get("payment.wallet-not-found")));
        }
        if (wallet.getBalance() < amount) {
            return new Check.Fail(new PaymentResponse.InsufficientBalance(messages.get("payment.insufficient-balance")));
        }
        return new Check.Pass();
    }
}
