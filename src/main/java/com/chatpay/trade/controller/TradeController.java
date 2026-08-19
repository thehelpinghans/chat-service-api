package com.chatpay.trade.controller;

import com.chatpay.common.broadcast.ChatRoomBroadcaster;
import com.chatpay.common.config.TenantFilter;
import com.chatpay.common.message.MessageResolver;
import com.chatpay.trade.dto.PaymentResponse;
import com.chatpay.trade.dto.TradeCreateResponse;
import com.chatpay.trade.service.TradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

// 판매자(테넌트) sdk.js 위젯이 Bearer 세션으로 직접 호출하는 결제 요청 생성 API.
// 판매자가 결제 요청을 트리거하면 Trade(PENDING) + ChatMessage(PAYMENT_REQUEST)를 한 트랜잭션으로 생성한다.
// (논리설계.md 결제 프로세스 플로우 1단계 근거)
// 금액은 항상 chatRoom.getItem().getPrice() 스냅샷 고정 — 협의/네고 없음, 클라이언트 입력값이 없어서 요청 바디 없음.
// 판매자 전용 가드(userId != null → 403)는 TradeService.createTrade 내부에서 처리.
@RestController
@RequestMapping("/api/v1/user/chat-rooms")
@RequiredArgsConstructor
@Tag(name = "Trade API", description = "결제 요청 생성 API")
public class TradeController {

    private final TradeService tradeService;
    private final ChatRoomBroadcaster chatRoomBroadcaster;
    private final MessageResolver messages;

    @PostMapping("/{chatRoomId}/trades")
    @Operation(summary = "결제 요청 생성")
    public ResponseEntity<TradeCreateResponse.Created> createTrade(
            @PathVariable Long chatRoomId,
            @RequestAttribute(value = TenantFilter.CHAT_ROOM_ATTRIBUTE, required = false) Long tokenChatRoomId,
            @RequestAttribute(value = TenantFilter.USER_ATTRIBUTE, required = false) Long userId) {

        TradeCreateResponse response = tradeService.createTrade(chatRoomId, tokenChatRoomId, userId);

        if (response instanceof TradeCreateResponse.Created success) {
            chatRoomBroadcaster.broadcast(chatRoomId, success);
        }

        return switch (response) {
            case TradeCreateResponse.Created r -> ResponseEntity.status(HttpStatus.CREATED).body(r);

            case TradeCreateResponse.SellerOnly _ -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, messages.get("trade.create.seller-only"));

            case TradeCreateResponse.ChatRoomAccessDenied _ -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, messages.get("trade.create.chatroom-access-denied"));

            case TradeCreateResponse.ChatRoomNotFound _ -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, messages.get("trade.create.chatroom-not-found"));
        };
    }

    @PostMapping("/{chatRoomId}/trades/{chatMessageId}/pay")
    @Operation(summary = "결제 처리")
    public ResponseEntity<PaymentResponse.PaymentSuccess> payTrade(
            @PathVariable Long chatRoomId,
            @PathVariable Long chatMessageId,
            @RequestAttribute(value = TenantFilter.CHAT_ROOM_ATTRIBUTE, required = false) Long tokenChatRoomId,
            @RequestAttribute(value = TenantFilter.USER_ATTRIBUTE, required = false) Long userId) {

        PaymentResponse response = tradeService.payTrade(chatRoomId, chatMessageId, tokenChatRoomId, userId);

        if (response instanceof PaymentResponse.PaymentSuccess success) {
            chatRoomBroadcaster.broadcast(chatRoomId, success);
        }

        return switch (response) {
            case PaymentResponse.PaymentSuccess r -> ResponseEntity.ok(r);

            case PaymentResponse.PaymentNotFound _ -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, messages.get("payment.not-found"));

            case PaymentResponse.ChatRoomAccessDenied _ -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, messages.get("payment.chatroom-access-denied"));

            case PaymentResponse.InvalidRequestType _ -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get("payment.invalid-request-type"));

            case PaymentResponse.PaymentOwnerMismatch _ -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, messages.get("payment.owner-mismatch"));

            case PaymentResponse.PaymentAlreadyProcessed _ -> throw new ResponseStatusException(HttpStatus.CONFLICT, messages.get("payment.already-processed"));

            case PaymentResponse.WalletNotFound _ -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get("payment.wallet-not-found"));

            case PaymentResponse.InsufficientBalance _ -> throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, messages.get("payment.insufficient-balance"));
        };
    }
}
