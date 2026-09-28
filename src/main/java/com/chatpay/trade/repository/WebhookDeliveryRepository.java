package com.chatpay.trade.repository;

import com.chatpay.trade.domain.Trade;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WebhookDeliveryRepository extends Repository<Trade, Long> {

    interface WebhookDeliveryRow {
        Long getTradeId();
        String getWebhookUrl();
        String getExternalItemId();
        Long getAmount();
    }
    // tenant/item 데이터를 조인 한 번으로 가져옴(네이티브 쿼리, @TenantId 필터 미적용)
    @Query(value = """
        SELECT t.id AS tradeId, te.webhook_url AS webhookUrl, i.external_item_id AS externalItemId, t.amount AS amount
        FROM trade t
        JOIN tenant te ON t.tenant_id = te.id
        JOIN item i ON t.item_id = i.id
        WHERE t.id = :tradeId
        """, nativeQuery = true)
    Optional<WebhookDeliveryRow> findWebhookDeliveryData(@Param("tradeId") Long tradeId);
}
