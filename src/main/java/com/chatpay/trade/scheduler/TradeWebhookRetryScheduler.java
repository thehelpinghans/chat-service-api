package com.chatpay.trade.scheduler;

import com.chatpay.common.multitenancy.BackgroundTenantContextHolder;
import com.chatpay.trade.repository.TradeWebhookRetryRepository;
import com.chatpay.trade.service.webhook.TradeWebhookRetryService;
import com.chatpay.trade.service.webhook.TradeWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeWebhookRetryScheduler {

    private final TradeWebhookRetryService tradeWebhookRetryService;
    private final TradeWebhookService tradeWebhookService;

    @Scheduled(fixedDelay = 30_000)
    public void processDueRetries() {
        List<TradeWebhookRetryRepository.DueRetryRow> dueRetries = tradeWebhookRetryService.findDueRetries();
        if (dueRetries.isEmpty()) {
            return;
        }
        log.info("Processing {} due webhook retries", dueRetries.size());
        for (TradeWebhookRetryRepository.DueRetryRow row : dueRetries) {
            try (BackgroundTenantContextHolder.Scope ignored = BackgroundTenantContextHolder.open(row.getTenantId())) {
                tradeWebhookService.attemptRetryDelivery(row.getTradeId());
            } catch (Exception e) {
                log.error("Unexpected failure processing tradeId={}, skipping to next row", row.getTradeId(), e);
            }
        }
    }
}
