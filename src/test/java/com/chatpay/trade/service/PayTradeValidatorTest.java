package com.chatpay.trade.service;

import com.chatpay.Fixture;
import com.chatpay.chat.domain.ChatMessage;
import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.domain.MessageType;
import com.chatpay.common.domain.Item;
import com.chatpay.common.domain.User;
import com.chatpay.common.message.MessageResolver;
import com.chatpay.trade.domain.Trade;
import com.chatpay.trade.domain.TradeStatus;
import com.chatpay.trade.domain.Wallet;
import com.chatpay.trade.dto.PaymentResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PayTradeValidatorTest {

    @Mock
    private MessageResolver messages;

    private final User buyer = Fixture.createUser(1L, "buyer-1");
    private final User otherBuyer = Fixture.createUser(2L, "buyer-2");
    private final Item item = Fixture.createItem(1L, "item-1", "테스트 상품", 10000L);
    private final ChatRoom chatRoom = Fixture.createChatRoom(1L, buyer, item);

    @Test
    @DisplayName("chatRoomId一致確認")
    void passesWhenChatRoomIdMatches() {
        // when
        PayTradeValidator.Check result = PayTradeValidator.checkChatRoomAccess(1L, 1L, messages);

        // then
        assertThat(result).isInstanceOf(PayTradeValidator.Check.Pass.class);
    }

    @Test
    @DisplayName("chatRoomId不一致")
    void deniesWhenChatRoomIdMismatches() {
        // given
        given(messages.get(anyString())).willReturn("access denied");

        // when
        PayTradeValidator.Check result = PayTradeValidator.checkChatRoomAccess(1L, 2L, messages);

        // then
        assertThat(result).isInstanceOfSatisfying(PayTradeValidator.Check.Fail.class,
                fail -> assertThat(fail.response()).isInstanceOf(PaymentResponse.ChatRoomAccessDenied.class));
    }

    @Test
    @DisplayName("メッセージ未存在")
    void notFoundWhenMessageIsNull() {
        // given
        given(messages.get(anyString())).willReturn("not found");

        // when
        PayTradeValidator.Check result = PayTradeValidator.checkPaymentRequestMessage(null, 1L, messages);

        // then
        assertThat(result).isInstanceOfSatisfying(PayTradeValidator.Check.Fail.class,
                fail -> assertThat(fail.response()).isInstanceOf(PaymentResponse.PaymentNotFound.class));
    }

    @Test
    @DisplayName("他チャットルームのメッセージ")
    void deniesWhenMessageBelongsToOtherChatRoom() {
        // given
        given(messages.get(anyString())).willReturn("access denied");
        ChatMessage message = Fixture.createChatMessage(1L, chatRoom, null, "결제 요청", MessageType.PAYMENT_REQUEST);

        // when
        PayTradeValidator.Check result = PayTradeValidator.checkPaymentRequestMessage(message, 999L, messages);

        // then
        assertThat(result).isInstanceOfSatisfying(PayTradeValidator.Check.Fail.class,
                fail -> assertThat(fail.response()).isInstanceOf(PaymentResponse.ChatRoomAccessDenied.class));
    }

    @Test
    @DisplayName("決済リクエストでないメッセージ種別")
    void invalidWhenMessageTypeIsNotPaymentRequest() {
        // given
        given(messages.get(anyString())).willReturn("invalid type");
        ChatMessage message = Fixture.createChatMessage(1L, chatRoom, buyer, "일반 메시지", MessageType.TEXT);

        // when
        PayTradeValidator.Check result = PayTradeValidator.checkPaymentRequestMessage(message, chatRoom.getId(), messages);

        // then
        assertThat(result).isInstanceOfSatisfying(PayTradeValidator.Check.Fail.class,
                fail -> assertThat(fail.response()).isInstanceOf(PaymentResponse.InvalidRequestType.class));
    }

    @Test
    @DisplayName("有効な決済リクエストメッセージ")
    void passesForValidPaymentRequestMessage() {
        // given
        ChatMessage message = Fixture.createChatMessage(1L, chatRoom, null, "결제 요청", MessageType.PAYMENT_REQUEST);

        // when
        PayTradeValidator.Check result = PayTradeValidator.checkPaymentRequestMessage(message, chatRoom.getId(), messages);

        // then
        assertThat(result).isInstanceOf(PayTradeValidator.Check.Pass.class);
    }

    @Test
    @DisplayName("取引未存在")
    void notFoundWhenTradeIsNull() {
        // given
        given(messages.get(anyString())).willReturn("not found");

        // when
        PayTradeValidator.Check result = PayTradeValidator.checkPayableTrade(null, buyer.getId(), messages);

        // then
        assertThat(result).isInstanceOfSatisfying(PayTradeValidator.Check.Fail.class,
                fail -> assertThat(fail.response()).isInstanceOf(PaymentResponse.PaymentNotFound.class));
    }

    @Test
    @DisplayName("オペレーターによる決済試行")
    void ownerMismatchWhenSellerAttemptsPayment() {
        // given
        given(messages.get(anyString())).willReturn("owner mismatch");
        ChatMessage message = Fixture.createChatMessage(1L, chatRoom, null, "결제 요청", MessageType.PAYMENT_REQUEST);
        Trade trade = Fixture.createTrade(1L, buyer, item, item.getName(), item.getPrice(), chatRoom, message);

        // when
        PayTradeValidator.Check result = PayTradeValidator.checkPayableTrade(trade, null, messages);

        // then
        assertThat(result).isInstanceOfSatisfying(PayTradeValidator.Check.Fail.class,
                fail -> assertThat(fail.response()).isInstanceOf(PaymentResponse.PaymentOwnerMismatch.class));
    }

    @Test
    @DisplayName("所有者不一致")
    void ownerMismatchWhenDifferentBuyerAttempts() {
        // given
        given(messages.get(anyString())).willReturn("owner mismatch");
        ChatMessage message = Fixture.createChatMessage(1L, chatRoom, null, "결제 요청", MessageType.PAYMENT_REQUEST);
        Trade trade = Fixture.createTrade(1L, buyer, item, item.getName(), item.getPrice(), chatRoom, message);

        // when
        PayTradeValidator.Check result = PayTradeValidator.checkPayableTrade(trade, otherBuyer.getId(), messages);

        // then
        assertThat(result).isInstanceOfSatisfying(PayTradeValidator.Check.Fail.class,
                fail -> assertThat(fail.response()).isInstanceOf(PaymentResponse.PaymentOwnerMismatch.class));
    }

    @Test
    @DisplayName("決済済み取引")
    void alreadyProcessedWhenTradeIsNotPending() {
        // given
        given(messages.get(anyString())).willReturn("already processed");
        ChatMessage message = Fixture.createChatMessage(1L, chatRoom, null, "결제 요청", MessageType.PAYMENT_REQUEST);
        Trade trade = Fixture.createTradeWithStatus(1L, buyer, item, item.getName(), item.getPrice(), chatRoom, message, TradeStatus.PAID);

        // when
        PayTradeValidator.Check result = PayTradeValidator.checkPayableTrade(trade, buyer.getId(), messages);

        // then
        assertThat(result).isInstanceOfSatisfying(PayTradeValidator.Check.Fail.class,
                fail -> assertThat(fail.response()).isInstanceOf(PaymentResponse.PaymentAlreadyProcessed.class));
    }

    @Test
    @DisplayName("所有者のPENDING取引")
    void passesForOwnersPendingTrade() {
        // given
        ChatMessage message = Fixture.createChatMessage(1L, chatRoom, null, "결제 요청", MessageType.PAYMENT_REQUEST);
        Trade trade = Fixture.createTrade(1L, buyer, item, item.getName(), item.getPrice(), chatRoom, message);

        // when
        PayTradeValidator.Check result = PayTradeValidator.checkPayableTrade(trade, buyer.getId(), messages);

        // then
        assertThat(result).isInstanceOf(PayTradeValidator.Check.Pass.class);
    }

    @Test
    @DisplayName("ウォレット未存在")
    void walletNotFoundWhenWalletIsNull() {
        // given
        given(messages.get(anyString())).willReturn("wallet not found");

        // when
        PayTradeValidator.Check result = PayTradeValidator.checkSufficientWallet(null, 10000L, messages);

        // then
        assertThat(result).isInstanceOfSatisfying(PayTradeValidator.Check.Fail.class,
                fail -> assertThat(fail.response()).isInstanceOf(PaymentResponse.WalletNotFound.class));
    }

    @Test
    @DisplayName("残高不足")
    void insufficientBalanceWhenBalanceIsLow() {
        // given
        given(messages.get(anyString())).willReturn("insufficient balance");
        Wallet wallet = Fixture.createWallet(buyer, 5000L);

        // when
        PayTradeValidator.Check result = PayTradeValidator.checkSufficientWallet(wallet, 10000L, messages);

        // then
        assertThat(result).isInstanceOfSatisfying(PayTradeValidator.Check.Fail.class,
                fail -> assertThat(fail.response()).isInstanceOf(PaymentResponse.InsufficientBalance.class));
    }

    @Test
    @DisplayName("残高と決済額が同一")
    void passesWhenBalanceEqualsAmount() {
        // given
        Wallet wallet = Fixture.createWallet(buyer, 10000L);

        // when
        PayTradeValidator.Check result = PayTradeValidator.checkSufficientWallet(wallet, 10000L, messages);

        // then
        assertThat(result).isInstanceOf(PayTradeValidator.Check.Pass.class);
    }
}
