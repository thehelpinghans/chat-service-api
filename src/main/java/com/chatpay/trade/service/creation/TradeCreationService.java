package com.chatpay.trade.service.creation;

import com.chatpay.chat.domain.ChatMessage;
import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.service.message.create.ChatMessageCreateService;
import com.chatpay.chat.service.room.ChatRoomService;
import com.chatpay.common.domain.Item;
import com.chatpay.common.message.MessageResolver;
import com.chatpay.trade.domain.Trade;
import com.chatpay.trade.domain.TradeStatus;
import com.chatpay.trade.dto.TradeCreateResponse;
import com.chatpay.trade.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeCreationService {

    private final ChatRoomService chatRoomService;
    private final ChatMessageCreateService chatMessageCreateService;
    private final TradeRepository tradeRepository;
    private final MessageResolver messages;

    @Transactional
    public TradeCreateResponse createTrade(Long chatRoomId, Long tokenChatRoomId, Long userId) {

        if (TradeCreateValidator.checkSellerOnly(userId, messages)
                instanceof TradeCreateValidator.Check.Fail(TradeCreateResponse response)) return response;

        if (TradeCreateValidator.checkChatRoomAccess(chatRoomId, tokenChatRoomId, messages)
                instanceof TradeCreateValidator.Check.Fail(TradeCreateResponse response)) return response;

        ChatRoom chatRoom = chatRoomService.findChatRoomById(chatRoomId).orElse(null);
        if (TradeCreateValidator.checkChatRoomExists(chatRoom, messages)
                instanceof TradeCreateValidator.Check.Fail(TradeCreateResponse response)) return response;

        Trade existingPendingTrade = tradeRepository.findByChatRoomIdAndTradeStatus(chatRoom.getId(), TradeStatus.PENDING).orElse(null);
        if (existingPendingTrade != null) {
            ChatMessage existingMessage = existingPendingTrade.getChatMessage();
            return new TradeCreateResponse.Found(existingMessage.getId(), existingMessage.getMessageType(), null, existingMessage.getCreatedAt());
        }

        ChatMessage paymentMessage = chatMessageCreateService.createPaymentRequestMessage(chatRoom);

        Item item = chatRoom.getItem();

        log.info("Creating PENDING trade: chatRoomId={}, itemId={}, amount={}", chatRoomId, item.getId(), item.getPrice());
        tradeRepository.save(Trade.create(chatRoom.getUser(), item, item.getName(), item.getPrice(), chatRoom, paymentMessage));

        return new TradeCreateResponse.Created(paymentMessage.getId(), paymentMessage.getMessageType(), null, paymentMessage.getCreatedAt());
    }
}
