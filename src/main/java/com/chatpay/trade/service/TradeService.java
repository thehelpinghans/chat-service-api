package com.chatpay.trade.service;

import com.chatpay.chat.domain.ChatMessage;
import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.dto.message.ChatMessageResponse;
import com.chatpay.common.domain.Item;
import com.chatpay.trade.domain.Trade;
import com.chatpay.trade.domain.TradeStatus;
import com.chatpay.trade.domain.Wallet;
import com.chatpay.trade.domain.WalletTransaction;
import com.chatpay.trade.dto.PaymentResponse;
import com.chatpay.trade.dto.TradeCreateResponse;
import com.chatpay.trade.repository.TradeRepository;
import com.chatpay.trade.repository.WalletRepository;
import com.chatpay.trade.repository.WalletTransactionRepository;
import com.chatpay.chat.service.message.ChatMessageService;
import com.chatpay.chat.service.room.ChatRoomService;
import com.chatpay.common.message.MessageResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TradeService {

    private final ChatRoomService chatRoomService;
    private final ChatMessageService chatMessageService;
    private final TradeRepository tradeRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final MessageResolver messages;

    @Transactional
    public TradeCreateResponse createTrade(Long chatRoomId, Long tokenChatRoomId, Long userId) {

        if (userId != null) {
            return new TradeCreateResponse.SellerOnly(messages.get("trade.create.seller-only"));
        }

        if (!chatRoomId.equals(tokenChatRoomId)) {
            return new TradeCreateResponse.ChatRoomAccessDenied(messages.get("trade.create.chatroom-access-denied"));
        }

        ChatRoom chatRoom = chatRoomService.findChatRoomById(chatRoomId).orElse(null);
        if (chatRoom == null) {
            return new TradeCreateResponse.ChatRoomNotFound(messages.get("trade.create.chatroom-not-found"));
        }

        Trade existingPendingTrade = tradeRepository.findByChatRoomIdAndTradeStatus(chatRoom.getId(), TradeStatus.PENDING).orElse(null);
        if (existingPendingTrade != null) {
            ChatMessage existingMessage = existingPendingTrade.getChatMessage();
            return new TradeCreateResponse.Found(existingMessage.getId(), existingMessage.getMessageType(), null, existingMessage.getCreatedAt());
        }

        ChatMessage paymentMessage = chatMessageService.createPaymentRequestMessage(chatRoom);

        Item item = chatRoom.getItem();

        tradeRepository.save(Trade.create(chatRoom.getUser(), item, item.getName(), item.getPrice(), chatRoom, paymentMessage));

        return new TradeCreateResponse.Created(paymentMessage.getId(), paymentMessage.getMessageType(),null, paymentMessage.getCreatedAt());
    }

    @Transactional
    public PaymentResponse payTrade(Long chatRoomId, Long chatMessageId, Long tokenChatRoomId, Long userId) {

        if (PayTradeValidator.checkChatRoomAccess(chatRoomId, tokenChatRoomId, messages)
                instanceof PayTradeValidator.Check.Fail(PaymentResponse response)) return response;

        ChatMessage message = chatMessageService.findChatMessageById(chatMessageId).orElse(null);
        if (PayTradeValidator.checkPaymentRequestMessage(message, chatRoomId, messages)
                instanceof PayTradeValidator.Check.Fail(PaymentResponse response)) return response;

        Trade currentTrade = tradeRepository.findByChatMessageId(chatMessageId).orElse(null);
        if (PayTradeValidator.checkPayableTrade(currentTrade, userId, messages)
                instanceof PayTradeValidator.Check.Fail(PaymentResponse response)) return response;

        Wallet wallet = walletRepository.findById(userId).orElse(null);
        if (PayTradeValidator.checkSufficientWallet(wallet, currentTrade.getAmount(), messages)
                instanceof PayTradeValidator.Check.Fail(PaymentResponse response)) return response;

        wallet.pay(currentTrade.getAmount());

        currentTrade.changeStatus(TradeStatus.PAID);

        // flushを省略すると、上2行のUPDATE(dirty checking由来)はコミット時まで遅延し、INSERTより後で実行される。
        // INSERTは先に共有ロックを取得するため、同時決済時は双方のトランザクションが排他ロックへの切替待ちとなり、デッドロックが発生する。
        walletRepository.flush();

        walletTransactionRepository.save(
                WalletTransaction.createPayment(wallet, currentTrade, currentTrade.getAmount()));

        return new PaymentResponse.PaymentSuccess(chatMessageId, currentTrade.getTradeStatus());
    }
}
