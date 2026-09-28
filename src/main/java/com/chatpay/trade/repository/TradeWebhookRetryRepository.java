package com.chatpay.trade.repository;

import com.chatpay.trade.domain.TradeWebhookRetry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TradeWebhookRetryRepository extends JpaRepository<TradeWebhookRetry, Long> {

    // 스케줄러 전용 — 네이티브 쿼리라 @TenantId 필터 미적용
    @Query(value = """
        SELECT t.id AS tradeId, t.tenant_id AS tenantId
        FROM trade t
        JOIN trade_webhook_retry twr ON t.id = twr.id
        WHERE t.trade_status = 'PAID'
          AND twr.next_attempt_at <= NOW()
          AND twr.attempt_count < :maxAttempts
        """, nativeQuery = true)
    List<DueRetryRow> findDueRetries(@Param("maxAttempts") int maxAttempts);

    interface DueRetryRow {
        Long getTradeId();
        Long getTenantId();
    }
}
