package com.chatpay.saas.service.trade;

import com.chatpay.saas.domain.*;
import com.chatpay.saas.dto.chat.message.ChatMessageResponse;
import com.chatpay.saas.repository.TradeRepository;
import com.chatpay.saas.service.chat.message.ChatMessageService;
import com.chatpay.saas.service.chat.room.ChatRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TradeService {

    private final ChatRoomService chatRoomService;
    private final ChatMessageService chatMessageService;
    private final TradeRepository tradeRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // Trade(PENDING) 생성. ChatMessage(PAYMENT_REQUEST) 저장은 ChatMessageService에 위임(도메인 경계 준수),
    // 브로드캐스트는 Trade까지 저장이 끝난 뒤 여기서 마지막에 호출(순서 보장 — Trade 생성 전 메시지부터 뜨는 것 방지).
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
}
