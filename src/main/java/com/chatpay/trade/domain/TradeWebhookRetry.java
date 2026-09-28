package com.chatpay.trade.domain;

import com.chatpay.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(indexes = @Index(name = "idx_trade_webhook_retry_next_attempt_at", columnList = "next_attempt_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradeWebhookRetry extends BaseEntity {

    public static final int MAX_ATTEMPTS = 8;

    @Id
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id")
    private Trade trade;

    @Column(nullable = false)
    private Integer attemptCount;

    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    public static TradeWebhookRetry create(Trade trade) {
        TradeWebhookRetry retry = new TradeWebhookRetry();
        retry.trade = trade;
        retry.attemptCount = 1;

        retry.nextAttemptAt = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(backoffMinutes(retry.attemptCount));
        return retry;
    }

    // outbox 등록용 — create()와 달리 백오프 없이 즉시 마감(1차 시도 전이라 대기시간 불필요)
    public static TradeWebhookRetry createDueNow(Trade trade) {
        TradeWebhookRetry retry = new TradeWebhookRetry();
        retry.trade = trade;
        retry.attemptCount = 1;
        retry.nextAttemptAt = LocalDateTime.now(ZoneOffset.UTC);
        return retry;
    }

    public void recordFailure() {
        this.attemptCount++;
        this.nextAttemptAt = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(backoffMinutes(this.attemptCount));
    }

    public boolean isExhausted() {
        return this.attemptCount >= MAX_ATTEMPTS;
    }

    // 1분 * 4^(attemptCount-1) — 1, 4, 16, 64, 256, 1024, 4096분(약 3일 19시간)
    private static long backoffMinutes(int attemptCount) {
        return (long) Math.pow(4, attemptCount - 1);
    }
}
