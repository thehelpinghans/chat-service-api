package com.chatpay.trade.service.webhook;

import com.chatpay.trade.dto.WebhookPayload;
import com.chatpay.trade.gateway.WebhookGateway;
import com.chatpay.trade.repository.WebhookDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeWebhookService {

    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final TradeWebhookRetryService tradeWebhookRetryService;
    private final WebhookGateway webhookGateway;

    // 1차 시도/재시도 구분 없이 스케줄러가 트리거
    public void attemptRetryDelivery(Long tradeId) {
        sendAndHandleResult(tradeId);
    }

    // 실행 담당 — 데이터 조회, HTTP 전송, 결과에 따른 성공/실패 위임까지 한 번에 처리
    private void sendAndHandleResult(Long tradeId) {
        Optional<WebhookDeliveryRepository.WebhookDeliveryRow> data =
                webhookDeliveryRepository.findWebhookDeliveryData(tradeId);

        if (data.isEmpty()) {
            // recordRetryFailure 호출 필수 — 안 하면 row가 안 바뀌어 다음 틱에 무한 반복됨
            log.error("Webhook delivery aborted, data not found for tradeId={}", tradeId);
            tradeWebhookRetryService.recordRetryFailure(tradeId);
            return;
        }

        WebhookDeliveryRepository.WebhookDeliveryRow row = data.get();
        WebhookPayload payload = new WebhookPayload(row.getTradeId(), row.getExternalItemId(), row.getAmount());
        boolean success = webhookGateway.send(row.getWebhookUrl(), payload);

        if (success) {
            tradeWebhookRetryService.markDelivered(tradeId);
        } else {
            tradeWebhookRetryService.recordRetryFailure(tradeId);
        }
    }

}
