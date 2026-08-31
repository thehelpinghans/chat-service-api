package com.chatpay.trade.controller;

import com.chatpay.common.broadcast.ChatRoomBroadcaster;
import com.chatpay.common.config.TenantFilter;
import com.chatpay.trade.dto.PaymentResponse;
import com.chatpay.trade.dto.TradeCreateResponse;
import com.chatpay.trade.service.TradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user/chat-rooms")
@RequiredArgsConstructor
@Tag(name = "Trade API", description = "결제 요청 생성 API")
public class TradeController {

    private final TradeService tradeService;
    private final ChatRoomBroadcaster chatRoomBroadcaster;

    @PostMapping("/{chatRoomId}/trades")
    @Operation(summary = "결제 요청 생성")
    public ResponseEntity<? extends TradeCreateResponse> createTrade(
            @PathVariable Long chatRoomId,
            @RequestAttribute(value = TenantFilter.CHAT_ROOM_ATTRIBUTE, required = false) Long tokenChatRoomId,
            @RequestAttribute(value = TenantFilter.USER_ATTRIBUTE, required = false) Long userId) {

        TradeCreateResponse response = tradeService.createTrade(chatRoomId, tokenChatRoomId, userId);

        if (response instanceof TradeCreateResponse.Created success) {
            chatRoomBroadcaster.send(chatRoomId, success);
        }

        return switch (response) {
            case TradeCreateResponse.Found r -> ResponseEntity.ok(r);

            case TradeCreateResponse.Created r -> ResponseEntity.status(HttpStatus.CREATED).body(r);

            case TradeCreateResponse.SellerOnly r -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(r);

            case TradeCreateResponse.ChatRoomAccessDenied r -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(r);

            case TradeCreateResponse.ChatRoomNotFound r -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(r);
        };
    }

    @PostMapping("/{chatRoomId}/trades/{chatMessageId}/pay")
    @Operation(summary = "결제 처리")
    public ResponseEntity<? extends PaymentResponse> payTrade(
            @PathVariable Long chatRoomId,
            @PathVariable Long chatMessageId,
            @RequestAttribute(value = TenantFilter.CHAT_ROOM_ATTRIBUTE, required = false) Long tokenChatRoomId,
            @RequestAttribute(value = TenantFilter.USER_ATTRIBUTE, required = false) Long userId) {

        PaymentResponse response = tradeService.payTrade(chatRoomId, chatMessageId, tokenChatRoomId, userId);

        if (response instanceof PaymentResponse.PaymentSuccess success) {
            chatRoomBroadcaster.send(chatRoomId, success);
        }

        return switch (response) {
            case PaymentResponse.PaymentSuccess r -> ResponseEntity.ok(r);

            case PaymentResponse.PaymentNotFound r -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(r);

            case PaymentResponse.ChatRoomAccessDenied r -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(r);

            case PaymentResponse.InvalidRequestType r -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body(r);

            case PaymentResponse.PaymentOwnerMismatch r -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(r);

            case PaymentResponse.PaymentAlreadyProcessed r -> ResponseEntity.status(HttpStatus.CONFLICT).body(r);

            case PaymentResponse.WalletNotFound r -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body(r);

            case PaymentResponse.InsufficientBalance r -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(r);
        };
    }
}
