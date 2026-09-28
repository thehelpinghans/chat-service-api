package com.chatpay.trade.domain;

import com.chatpay.Fixture;
import com.chatpay.chat.domain.ChatMessage;
import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.domain.MessageType;
import com.chatpay.common.domain.Item;
import com.chatpay.common.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TradeWebhookRetryTest {

    private final User buyer = Fixture.createUser(1L, "buyer-1");
    private final Item item = Fixture.createItem(1L, "item-1", "테스트 상품", 10000L);
    private final ChatRoom chatRoom = Fixture.createChatRoom(1L, buyer, item);
    private final ChatMessage message = Fixture.createChatMessage(1L, chatRoom, null, "결제 요청", MessageType.PAYMENT_REQUEST);
    private final Trade trade = Fixture.createTrade(1L, buyer, item, item.getName(), item.getPrice(), chatRoom, message);

    @Test
    @DisplayName("最初の失敗記録で1回目試行・1分後の再試行になる")
    void createSetsFirstAttemptWithOneMinuteBackoff() {
        // when
        // TradeWebhookRetryはMySQLサーバのNOW()(UTC)と比較されるためZoneOffset.UTCで計算する — JVMの
        // デフォルトタイムゾーンとMySQLコンテナのタイムゾーンが違うと9時間ずれることを実測で確認済み
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
        TradeWebhookRetry retry = TradeWebhookRetry.create(trade);

        // then
        assertThat(retry.getAttemptCount()).isEqualTo(1);
        assertThat(retry.getNextAttemptAt()).isCloseTo(before.plusMinutes(1), within(2, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("recordFailureのたびに4倍で遅延が増える(指数バックオフ)")
    void recordFailureAppliesExponentialBackoff() {
        // given
        TradeWebhookRetry retry = TradeWebhookRetry.create(trade);

        // when
        // TradeWebhookRetryはMySQLサーバのNOW()(UTC)と比較されるためZoneOffset.UTCで計算する — JVMの
        // デフォルトタイムゾーンとMySQLコンテナのタイムゾーンが違うと9時間ずれることを実測で確認済み
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
        retry.recordFailure();

        // then
        assertThat(retry.getAttemptCount()).isEqualTo(2);
        assertThat(retry.getNextAttemptAt()).isCloseTo(before.plusMinutes(4), within(2, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("3回目の失敗は16分後の再試行になる")
    void thirdFailureBackoffIsSixteenMinutes() {
        // given
        TradeWebhookRetry retry = TradeWebhookRetry.create(trade);
        retry.recordFailure();

        // when
        // TradeWebhookRetryはMySQLサーバのNOW()(UTC)と比較されるためZoneOffset.UTCで計算する — JVMの
        // デフォルトタイムゾーンとMySQLコンテナのタイムゾーンが違うと9時間ずれることを実測で確認済み
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
        retry.recordFailure();

        // then
        assertThat(retry.getAttemptCount()).isEqualTo(3);
        assertThat(retry.getNextAttemptAt()).isCloseTo(before.plusMinutes(16), within(2, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("7回目の失敗まではまだ再試行対象")
    void notExhaustedBeforeMaxAttempts() {
        // given
        TradeWebhookRetry retry = TradeWebhookRetry.create(trade);
        ReflectionTestUtils.setField(retry, "attemptCount", TradeWebhookRetry.MAX_ATTEMPTS - 1);

        // then
        assertThat(retry.isExhausted()).isFalse();
    }

    @Test
    @DisplayName("MAX_ATTEMPTS到達で再試行を諦める")
    void exhaustedAtMaxAttempts() {
        // given
        TradeWebhookRetry retry = TradeWebhookRetry.create(trade);
        ReflectionTestUtils.setField(retry, "attemptCount", TradeWebhookRetry.MAX_ATTEMPTS);

        // then
        assertThat(retry.isExhausted()).isTrue();
    }
}
