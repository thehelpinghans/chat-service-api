package com.chatpay.integration.trade;

import com.chatpay.chat.domain.ChatMessage;
import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.domain.MessageType;
import com.chatpay.chat.repository.ChatMessageRepository;
import com.chatpay.chat.repository.ChatRoomRepository;
import com.chatpay.common.domain.Item;
import com.chatpay.common.domain.User;
import com.chatpay.common.repository.ItemRepository;
import com.chatpay.common.service.UserService;
import com.chatpay.trade.domain.Trade;
import com.chatpay.trade.domain.TradeStatus;
import com.chatpay.trade.domain.TradeWebhookRetry;
import com.chatpay.trade.repository.TradeRepository;
import com.chatpay.trade.repository.TradeWebhookRetryRepository;
import com.chatpay.trade.service.webhook.TradeWebhookRetryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// findDueRetries()のネイティブクエリ(trade_status='PAID' AND next_attempt_at<=NOW() AND attempt_count<maxAttempts)が
// 実MySQLで正しくフィルタするかを検証 — モックではSQL自体の正しさは検証できないため実DB必須。
@SpringBootTest
class TradeWebhookRetryRepositoryIntegrationTest {

    private static final String TENANT_ATTRIBUTE = "CURRENT_TENANT_ID";
    private static final Long SEED_TENANT_ID = 1L;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private TradeWebhookRetryRepository tradeWebhookRetryRepository;

    @Autowired
    private TradeWebhookRetryService tradeWebhookRetryService;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private UserService userService;

    @BeforeEach
    void setUpTenantContext() {
        ServletRequestAttributes attributes = new ServletRequestAttributes(new MockHttpServletRequest());
        attributes.setAttribute(TENANT_ATTRIBUTE, SEED_TENANT_ID, RequestAttributes.SCOPE_REQUEST);
        RequestContextHolder.setRequestAttributes(attributes);
    }

    @AfterEach
    void tearDownTenantContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private Trade createPaidTrade(String suffix) {
        User buyer = userService.getOrCreateUser("webhook-retry-repo-buyer-" + suffix);
        Item item = itemRepository.save(Item.create("webhook-retry-repo-item-" + suffix, "재시도 쿼리 테스트 상품", 1000L));
        ChatRoom chatRoom = chatRoomRepository.save(ChatRoom.create(buyer, item));
        ChatMessage message = chatMessageRepository.save(
                ChatMessage.create(chatRoom, null, "결제 요청 드립니다", MessageType.PAYMENT_REQUEST));
        Trade trade = tradeRepository.save(Trade.create(buyer, item, item.getName(), item.getPrice(), chatRoom, message));
        trade.changeStatus(TradeStatus.PAID);
        return tradeRepository.save(trade);
    }

    // 本番と同じ経路(registerImmediateDelivery→必要な分だけrecordRetryFailure)でTradeWebhookRetry行を作り、
    // その後nextAttemptAtだけ上書きする — 直接コンストラクトせず実サービスの経路を通す。
    private void seedRetry(Trade trade, int attemptCount, LocalDateTime nextAttemptAt) {
        tradeWebhookRetryService.registerImmediateDelivery(trade.getId());
        for (int i = 1; i < attemptCount; i++) {
            tradeWebhookRetryService.recordRetryFailure(trade.getId());
        }
        TradeWebhookRetry retry = tradeWebhookRetryRepository.findById(trade.getId()).orElseThrow();
        ReflectionTestUtils.setField(retry, "nextAttemptAt", nextAttemptAt);
        tradeWebhookRetryRepository.save(retry);
    }

    @Test
    @DisplayName("PAID・期限到来・試行回数未満のtradeのみ再試行対象として返る")
    void returnsOnlyDuePaidTradesUnderMaxAttempts() {
        // given
        // next_attempt_at <= NOW() はMySQLサーバのNOW()(このコンテナはUTC)と比較されるため、
        // JVMのデフォルトタイムゾーン(KST等)に依存しないようUTCで組み立てる
        // — 本番コードのTradeWebhookRetryもZoneOffset.UTCで統一済み
        LocalDateTime past = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5);
        LocalDateTime future = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);

        Trade dueTrade = createPaidTrade("due");
        seedRetry(dueTrade, 2, past);

        Trade notYetDueTrade = createPaidTrade("not-yet-due");
        seedRetry(notYetDueTrade, 1, future);

        Trade exhaustedTrade = createPaidTrade("exhausted");
        seedRetry(exhaustedTrade, TradeWebhookRetry.MAX_ATTEMPTS, past);

        // COMPLETEDになった後もretry行が残るケース(markDeliveredは行を削除しない)を再現 —
        // trade_status条件が無いと成功済みtradeまで再処理対象に混入してしまう
        Trade completedTrade = createPaidTrade("completed");
        seedRetry(completedTrade, 2, past);
        completedTrade.completeDelivery();
        tradeRepository.save(completedTrade);

        // when
        List<TradeWebhookRetryRepository.DueRetryRow> result =
                tradeWebhookRetryRepository.findDueRetries(TradeWebhookRetry.MAX_ATTEMPTS);
        List<Long> dueTradeIds = result.stream().map(TradeWebhookRetryRepository.DueRetryRow::getTradeId).toList();

        // then
        assertThat(dueTradeIds).contains(dueTrade.getId());
        assertThat(dueTradeIds).doesNotContain(
                notYetDueTrade.getId(), exhaustedTrade.getId(), completedTrade.getId());
    }
}
