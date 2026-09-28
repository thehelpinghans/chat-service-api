package com.chatpay.trade.service.webhook;

import com.chatpay.trade.domain.Trade;
import com.chatpay.trade.domain.TradeWebhookRetry;
import com.chatpay.trade.repository.TradeRepository;
import com.chatpay.trade.repository.TradeWebhookRetryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeWebhookRetryService {

    private final TradeRepository tradeRepository;
    private final TradeWebhookRetryRepository tradeWebhookRetryRepository;

    // 조회 전용 — 스케줄러가 폴링할 대상 찾기
    @Transactional(readOnly = true)
    public List<TradeWebhookRetryRepository.DueRetryRow> findDueRetries() {
        return tradeWebhookRetryRepository.findDueRetries(TradeWebhookRetry.MAX_ATTEMPTS);
    }

    // payTrade의 트랜잭션에 합류해 결제와 같이 커밋/롤백됨 — 형제 메서드들과 동일하게 id로 재조회해서
    // detached 상태로 넘어온 Trade를 그대로 persist하다 터지는 문제를 원천 차단
    @Transactional
    public void registerImmediateDelivery(Long tradeId) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new NoSuchElementException("trade not found, tradeId=" + tradeId));
        tradeWebhookRetryRepository.save(TradeWebhookRetry.createDueNow(trade));
        log.info("Registered webhook for immediate delivery: tradeId={}", tradeId);
    }

    // 성공 처리 — PAID→COMPLETED 전환, 다음 폴링 대상에서 제외시키는 역할
    @Transactional
    public void markDelivered(Long tradeId) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new NoSuchElementException("trade not found, tradeId=" + tradeId));
        trade.completeDelivery();
        log.info("Webhook delivered: tradeId={}", tradeId);
    }

    // 실패 처리 — attemptCount/nextAttemptAt 값을 올려 다음 재시도 시각을 미룸.
    // row는 registerImmediateDelivery가 항상 먼저 만들어두므로 신규 생성 분기는 없음.
    @Transactional
    public void recordRetryFailure(Long tradeId) {
        TradeWebhookRetry retry = tradeWebhookRetryRepository.findById(tradeId)
                .orElseThrow(() -> new NoSuchElementException("webhook retry record not found, tradeId=" + tradeId));
        retry.recordFailure();
        tradeWebhookRetryRepository.save(retry);
        if (retry.isExhausted()) {
            log.error("Webhook retries exhausted, giving up: tradeId={}, attemptCount={}", tradeId, retry.getAttemptCount());
        } else {
            log.info("Updated webhook retry record: tradeId={}, attemptCount={}", tradeId, retry.getAttemptCount());
        }
    }
}
