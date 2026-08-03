package com.chatpay.saas.controller.trade;

import com.chatpay.saas.config.TenantFilter;
import com.chatpay.saas.dto.chat.message.ChatMessageResponse;
import com.chatpay.saas.dto.trade.TradeResponse;
import com.chatpay.saas.service.trade.TradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/{chatRoomId}/trades")
    @Operation(summary = "결제 요청 생성")
    public ResponseEntity<ChatMessageResponse> createTrade(
            @PathVariable Long chatRoomId,
            @RequestAttribute(value = TenantFilter.CHAT_ROOM_ATTRIBUTE, required = false) Long tokenChatRoomId,
            @RequestAttribute(value = TenantFilter.USER_ATTRIBUTE, required = false) Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tradeService.createTrade(chatRoomId, tokenChatRoomId, userId));
    }

    // 구매자가 PAYMENT_REQUEST 메시지의 결제 버튼을 눌렀을 때 호출 — chatMessageId 기준 조회(논리설계.md L115).
    // 판매자 전용 가드(createTrade)와 반대로, 여기는 구매자 전용 — 소유권 체크는 TradeService.payTrade 내부에서 처리.
    //내일 단위테스트 확인 필요
    @PostMapping("/{chatRoomId}/trades/{chatMessageId}/pay")
    @Operation(summary = "결제 처리")
    public ResponseEntity<TradeResponse> payTrade(
            @PathVariable Long chatRoomId,
            @PathVariable Long chatMessageId,
            @RequestAttribute(value = TenantFilter.CHAT_ROOM_ATTRIBUTE, required = false) Long tokenChatRoomId,
            @RequestAttribute(value = TenantFilter.USER_ATTRIBUTE, required = false) Long userId) {
        return ResponseEntity.ok(tradeService.payTrade(chatRoomId, chatMessageId, tokenChatRoomId, userId));
    }
}
