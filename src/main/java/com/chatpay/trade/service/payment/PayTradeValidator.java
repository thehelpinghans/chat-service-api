package com.chatpay.trade.service.payment;

import com.chatpay.chat.domain.ChatMessage;
import com.chatpay.chat.domain.MessageType;
import com.chatpay.common.message.MessageResolver;
import com.chatpay.trade.domain.Trade;
import com.chatpay.trade.domain.TradeStatus;
import com.chatpay.trade.domain.Wallet;
import com.chatpay.trade.dto.PaymentResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

@Slf4j
class PayTradeValidator {

    sealed interface Check {
        record Pass() implements Check {}
        record Fail(PaymentResponse response) implements Check {}
    }

    /**
     * @return 通過時はPass。chatRoomIdがトークンのchatRoomIdと異なる場合はFail(ChatRoomAccessDenied)。
     */
    static Check checkChatRoomAccess(Long chatRoomId, Long tokenChatRoomId, MessageResolver messages) {
        log.debug("Validating chatRoomId: chatRoomId={}, tokenChatRoomId={}", chatRoomId, tokenChatRoomId);
        if (!Objects.equals(chatRoomId, tokenChatRoomId)) {
            return new Check.Fail(new PaymentResponse.ChatRoomAccessDenied(messages.get("payment.chatroom-access-denied")));
        }
        return new Check.Pass();
    }

    /**
     * @return 通過時はPass。messageが見つからない場合はFail(PaymentNotFound)、messageが別のチャットルームに
     * 属する場合はFail(ChatRoomAccessDenied)、PAYMENT_REQUESTでない場合はFail(InvalidRequestType)。
     */
    static Check checkPaymentRequestMessage(ChatMessage message, Long chatRoomId, MessageResolver messages) {
        log.debug("Validating payment request message: messageId={}, chatRoomId={}", message == null ? null : message.getId(), chatRoomId);
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

    /**
     * @return 通過時はPass。tradeが見つからない場合はFail(PaymentNotFound)、所有者でない場合は
     * Fail(PaymentOwnerMismatch)、PENDING以外の場合はFail(PaymentAlreadyProcessed)。
     */
    static Check checkPayableTrade(Trade trade, Long userId, MessageResolver messages) {
        log.debug("Validating payable trade: tradeId={}, userId={}", trade == null ? null : trade.getId(), userId);
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

    /**
     * @return 通過時はPass。walletが見つからない場合はFail(WalletNotFound)、残高不足の場合はFail(InsufficientBalance)。
     */
    static Check checkSufficientWallet(Wallet wallet, long amount, MessageResolver messages) {
        log.debug("Validating wallet balance: walletId={}, amount={}", wallet == null ? null : wallet.getId(), amount);
        if (wallet == null) {
            return new Check.Fail(new PaymentResponse.WalletNotFound(messages.get("payment.wallet-not-found")));
        }
        if (wallet.getBalance() < amount) {
            return new Check.Fail(new PaymentResponse.InsufficientBalance(messages.get("payment.insufficient-balance")));
        }
        return new Check.Pass();
    }
}
