package com.chatpay.saas.service.trade;

import com.chatpay.saas.domain.*;
import com.chatpay.saas.dto.chat.message.ChatMessageResponse;
import com.chatpay.saas.dto.trade.TradeResponse;
import com.chatpay.saas.repository.TradeRepository;
import com.chatpay.saas.repository.WalletRepository;
import com.chatpay.saas.repository.WalletTransactionRepository;
import com.chatpay.saas.service.chat.message.ChatMessageService;
import com.chatpay.saas.service.chat.room.ChatRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TradeService {

    private final ChatRoomService chatRoomService;
    private final ChatMessageService chatMessageService;
    private final TradeRepository tradeRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public ChatMessageResponse createTrade(Long chatRoomId, Long tokenChatRoomId, Long userId) {

        if (userId != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "잘못된 결제 요청입니다.");
        }

        if (!chatRoomId.equals(tokenChatRoomId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "유효한 세션이 아닙니다.");
        }

        ChatRoom chatRoom = chatRoomService.findChatRoomById(chatRoomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."));

        ChatMessage paymentMessage = chatMessageService.createPaymentRequestMessage(chatRoom);

        Item item = chatRoom.getItem();
        tradeRepository.save(Trade.create(chatRoom.getUser(), item, item.getName(), item.getPrice(), paymentMessage));

        ChatMessageResponse response = new ChatMessageResponse(paymentMessage.getId(), paymentMessage.getMessageType(), paymentMessage.getContent(),null, paymentMessage.getCreatedAt());
        messagingTemplate.convertAndSend("/topic/chat/" + chatRoomId, response);
        return response;
    }

    @Transactional
    public TradeResponse payTrade(Long chatRoomId, Long chatMessageId, Long tokenChatRoomId, Long userId) {
        validateChatRoomAccess(chatRoomId, tokenChatRoomId);

        ChatMessage payReqMessage = getValidPaymentRequestMessage(chatMessageId, chatRoomId);
        Trade currentTrade = payReqMessage.getTrade();
        validateOwner(currentTrade, userId);
        validatePendingStatus(currentTrade);

        Wallet wallet = getValidWallet(userId, currentTrade.getAmount());
        wallet.pay(currentTrade.getAmount());
        walletTransactionRepository.save(
                WalletTransaction.create(wallet, currentTrade, -currentTrade.getAmount(), TransactionType.PAYMENT));
        currentTrade.changeStatus(TradeStatus.PAID);

        TradeResponse response = new TradeResponse(chatMessageId, currentTrade.getTradeStatus());
        messagingTemplate.convertAndSend("/topic/chat/" + chatRoomId, response);
        return response;
    }

    //chatRoomId != tokenChatRoomId → 403 FORBIDDEN
    private void validateChatRoomAccess(Long chatRoomId, Long tokenChatRoomId) {
        if (!Objects.equals(chatRoomId, tokenChatRoomId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "잘못된 결제 요청입니다.");
        }
    }

    // chatRoomId 불일치 → 403 FORBIDDEN, trade 없음 → 404 NOT_FOUND, messageType != PAYMENT_REQUEST → 400 BAD_REQUEST
    private ChatMessage getValidPaymentRequestMessage(Long chatMessageId, Long chatRoomId) {
        ChatMessage message = chatMessageService.findChatMessageById(chatMessageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "결재 내역을 찾을 수 없습니다."));

        if (!Objects.equals(message.getChatRoom().getId(), chatRoomId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "잘못된 결제 요청입니다.");
        }
        if (message.getTrade() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "결재 내역을 찾을 수 없습니다");
        }
        if (message.getMessageType() != MessageType.PAYMENT_REQUEST) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 요청입니다");
        }
        return message;
    }

    // trade.user != userId → 403 FORBIDDEN
    private void validateOwner(Trade trade, Long userId) {
        if (!trade.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "사용자가 일치하지 않습니다.");
        }
    }

    // tradeStatus != PENDING → 409 CONFLICT
    private void validatePendingStatus(Trade trade) {
        if (trade.getTradeStatus() != TradeStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 결재 완료된 내역입니다");
        }
    }

    // wallet 없음 → 400 BAD_REQUEST, 잔액 부족 → 422 UNPROCESSABLE_ENTITY
    private Wallet getValidWallet(Long userId, long amount) {
        Wallet wallet = walletRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "지갑을 찾을 수 없습니다."));
        if (wallet.getBalance() < amount) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "잔액이 부족합니다");
        }
        return wallet;
    }
}
