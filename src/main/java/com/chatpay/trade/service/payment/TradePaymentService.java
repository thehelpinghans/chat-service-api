package com.chatpay.trade.service.payment;

import com.chatpay.chat.domain.ChatMessage;
import com.chatpay.chat.service.message.find.ChatMessageFindService;
import com.chatpay.common.message.MessageResolver;
import com.chatpay.trade.domain.Trade;
import com.chatpay.trade.domain.TradeStatus;
import com.chatpay.trade.domain.Wallet;
import com.chatpay.trade.dto.PaymentResponse;
import com.chatpay.trade.repository.TradeRepository;
import com.chatpay.trade.service.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradePaymentService {

    private final ChatMessageFindService chatMessageFindService;
    private final TradeRepository tradeRepository;
    private final WalletService walletService;
    private final MessageResolver messages;

    @Transactional
    public PaymentResponse payTrade(Long chatRoomId, Long chatMessageId, Long tokenChatRoomId, Long userId) {

        if (PayTradeValidator.checkChatRoomAccess(chatRoomId, tokenChatRoomId, messages)
                instanceof PayTradeValidator.Check.Fail(PaymentResponse response)) return response;

        ChatMessage message = chatMessageFindService.findChatMessageById(chatMessageId).orElse(null);
        if (PayTradeValidator.checkPaymentRequestMessage(message, chatRoomId, messages)
                instanceof PayTradeValidator.Check.Fail(PaymentResponse response)) return response;

        Trade currentTrade = tradeRepository.findByChatMessageId(chatMessageId).orElse(null);
        if (PayTradeValidator.checkPayableTrade(currentTrade, userId, messages)
                instanceof PayTradeValidator.Check.Fail(PaymentResponse response)) return response;

        Wallet wallet = walletService.findWalletById(userId).orElse(null);
        if (PayTradeValidator.checkSufficientWallet(wallet, currentTrade.getAmount(), messages)
                instanceof PayTradeValidator.Check.Fail(PaymentResponse response)) return response;

        log.info("Starting payment: chatMessageId={}, userId={}, amount={}", chatMessageId, userId, currentTrade.getAmount());
        walletService.debit(wallet, currentTrade.getAmount());

        currentTrade.changeStatus(TradeStatus.PAID);

        walletService.recordPayment(wallet, currentTrade, currentTrade.getAmount());

        log.info("Payment completed: chatMessageId={}, tradeId={}", chatMessageId, currentTrade.getId());
        return new PaymentResponse.PaymentSuccess(chatMessageId, currentTrade.getTradeStatus());
    }
}
