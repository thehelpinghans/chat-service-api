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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TradeService {

    private final ChatRoomService chatRoomService;
    private final ChatMessageService chatMessageService;
    private final TradeRepository tradeRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    //TODO 멱등성 확보가 안되어있음, 추후 추가 필요
    @Transactional
    public TradeCreateResponse createTrade(Long chatRoomId, Long tokenChatRoomId, Long userId) {

        if (userId != null) {
            return new TradeCreateResponse.SellerOnly();
        }

        if (!chatRoomId.equals(tokenChatRoomId)) {
            return new TradeCreateResponse.ChatRoomAccessDenied();
        }

        ChatRoom chatRoom = chatRoomService.findChatRoomById(chatRoomId).orElse(null);
        if (chatRoom == null) {
            return new TradeCreateResponse.ChatRoomNotFound();
        }

        ChatMessage paymentMessage = chatMessageService.createPaymentRequestMessage(chatRoom);

        Item item = chatRoom.getItem();
        tradeRepository.save(Trade.create(chatRoom.getUser(), item, item.getName(), item.getPrice(), paymentMessage));

        return new TradeCreateResponse.Created(paymentMessage.getId(), paymentMessage.getMessageType(),null, paymentMessage.getCreatedAt());
    }

    // 검증 실패는 예외가 아니라 PaymentResponse의 실패 레코드로 리턴 — 컨트롤러가 switch로 상태코드를 결정함
    @Transactional
    public PaymentResponse payTrade(Long chatRoomId, Long chatMessageId, Long tokenChatRoomId, Long userId) {

        if (PayTradeValidator.checkChatRoomAccess(chatRoomId, tokenChatRoomId)
                instanceof PayTradeValidator.Check.Fail(PaymentResponse response)) return response;

        ChatMessage message = chatMessageService.findChatMessageById(chatMessageId).orElse(null);
        if (PayTradeValidator.checkPaymentRequestMessage(message, chatRoomId)
                instanceof PayTradeValidator.Check.Fail(PaymentResponse response)) return response;

        Trade currentTrade = tradeRepository.findByChatMessageId(chatMessageId).orElse(null);
        if (PayTradeValidator.checkPayableTrade(currentTrade, userId)
                instanceof PayTradeValidator.Check.Fail(PaymentResponse response)) return response;

        Wallet wallet = walletRepository.findById(userId).orElse(null);
        if (PayTradeValidator.checkSufficientWallet(wallet, currentTrade.getAmount())
                instanceof PayTradeValidator.Check.Fail(PaymentResponse response)) return response;

        wallet.pay(currentTrade.getAmount());
        walletTransactionRepository.save(
                WalletTransaction.createPayment(wallet, currentTrade, currentTrade.getAmount()));
        currentTrade.changeStatus(TradeStatus.PAID);

        return new PaymentResponse.PaymentSuccess(chatMessageId, currentTrade.getTradeStatus());
    }
}
