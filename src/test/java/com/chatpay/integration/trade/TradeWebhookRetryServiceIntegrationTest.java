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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// markDelivered/recordRetryFailureは@Transactional境界とdetached entity回避が正しさの核심なので、
// Mockitoで動作を検証しても意味がない(このセッションで実際に踏んだ不具合の種類) — 実MySQLで検証する。
@SpringBootTest
class TradeWebhookRetryServiceIntegrationTest {

    private static final String TENANT_ATTRIBUTE = "CURRENT_TENANT_ID";
    private static final Long SEED_TENANT_ID = 1L;

    @Autowired
    private TradeWebhookRetryService tradeWebhookRetryService;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private TradeWebhookRetryRepository tradeWebhookRetryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
        User buyer = userService.getOrCreateUser("webhook-result-buyer-" + suffix);
        Item item = itemRepository.save(Item.create("webhook-result-item-" + suffix, "결과처리 테스트 상품", 1000L));
        ChatRoom chatRoom = chatRoomRepository.save(ChatRoom.create(buyer, item));
        ChatMessage message = chatMessageRepository.save(
                ChatMessage.create(chatRoom, null, "결제 요청 드립니다", MessageType.PAYMENT_REQUEST));
        Trade trade = tradeRepository.save(Trade.create(buyer, item, item.getName(), item.getPrice(), chatRoom, message));
        trade.changeStatus(TradeStatus.PAID);
        return tradeRepository.save(trade);
    }

    @Test
    @DisplayName("markDelivered呼び出しでPAID→COMPLETEDに遷移する")
    void markDeliveredCompletesTrade() {
        // given
        Trade trade = createPaidTrade("delivered");

        // when
        tradeWebhookRetryService.markDelivered(trade.getId());

        // then
        Trade reloaded = tradeRepository.findById(trade.getId()).orElseThrow();
        assertThat(reloaded.getTradeStatus()).isEqualTo(TradeStatus.COMPLETED);
    }

    @Test
    @DisplayName("outbox登録直後の失敗記録でattemptCountが2になる")
    void recordRetryFailureIncrementsAfterRegistration() {
        // given — 本番と同じくregisterImmediateDeliveryでrow作成(attemptCount=1)後に失敗を記録
        Trade trade = createPaidTrade("first-failure");
        tradeWebhookRetryService.registerImmediateDelivery(trade.getId());

        // when
        tradeWebhookRetryService.recordRetryFailure(trade.getId());

        // then
        TradeWebhookRetry retry = tradeWebhookRetryRepository.findById(trade.getId()).orElseThrow();
        assertThat(retry.getAttemptCount()).isEqualTo(2);
        assertThat(retry.isExhausted()).isFalse();
    }

    @Test
    @DisplayName("既存の再試行レコードはattemptCountが1つ増える")
    void recordRetryFailureIncrementsExistingRetry() {
        // given
        Trade trade = createPaidTrade("increment");
        tradeWebhookRetryService.registerImmediateDelivery(trade.getId());
        tradeWebhookRetryService.recordRetryFailure(trade.getId());

        // when
        tradeWebhookRetryService.recordRetryFailure(trade.getId());

        // then
        TradeWebhookRetry retry = tradeWebhookRetryRepository.findById(trade.getId()).orElseThrow();
        assertThat(retry.getAttemptCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("MAX_ATTEMPTS到達でisExhausted()がtrueになる")
    void recordRetryFailureReachesExhaustion() {
        // given
        Trade trade = createPaidTrade("exhaust");
        tradeWebhookRetryService.registerImmediateDelivery(trade.getId());

        // when
        for (int i = 1; i < TradeWebhookRetry.MAX_ATTEMPTS; i++) {
            tradeWebhookRetryService.recordRetryFailure(trade.getId());
        }

        // then
        TradeWebhookRetry retry = tradeWebhookRetryRepository.findById(trade.getId()).orElseThrow();
        assertThat(retry.getAttemptCount()).isEqualTo(TradeWebhookRetry.MAX_ATTEMPTS);
        assertThat(retry.isExhausted()).isTrue();
    }

    @Test
    @DisplayName("registerImmediateDeliveryは呼び出し元のトランザクションに合流し、それがロールバックされると一緒にロールバックされる")
    void registrationRollsBackWithOuterTransaction() {
        // given
        Trade trade = createPaidTrade("rollback-atomicity");

        // when — TransactionTemplateで外側のトランザクションを明示的に開始し、その中でregisterImmediateDelivery
        // (デフォルト伝播REQUIREDでこの外側トランザクションに合流)を呼んだ後、意図的に例外を投げてロールバックさせる。
        // これはTradePaymentService.payTradeの@Transactional境界を模したもの — 別Beanへの通常のクロスビーン
        // 呼び出しなので自己呼び出し問題は起きない。
        // isInstanceOf만으론 부족 — InvalidDataAccessApiUsageException 등도 RuntimeException이라
        // 의도한 예외가 아닌 다른 원인으로도 테스트가 통과해버릴 수 있어 메시지까지 명시적으로 확인
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            tradeWebhookRetryService.registerImmediateDelivery(trade.getId());
            throw new RuntimeException("intentional rollback trigger");
        })).isInstanceOf(RuntimeException.class)
                .hasMessage("intentional rollback trigger");

        // then — 外側がロールバックされたので、合流していたregisterImmediateDeliveryの書き込みも一緒に消える
        assertThat(tradeWebhookRetryRepository.findById(trade.getId())).isEmpty();
    }
}
