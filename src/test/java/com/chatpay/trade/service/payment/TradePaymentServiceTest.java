package com.chatpay.trade.service.payment;

import com.chatpay.Fixture;
import com.chatpay.chat.domain.ChatMessage;
import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.domain.MessageType;
import com.chatpay.chat.service.message.find.ChatMessageFindService;
import com.chatpay.common.domain.Item;
import com.chatpay.common.domain.User;
import com.chatpay.common.message.MessageResolver;
import com.chatpay.trade.domain.Trade;
import com.chatpay.trade.domain.TradeStatus;
import com.chatpay.trade.domain.Wallet;
import com.chatpay.trade.dto.PaymentResponse;
import com.chatpay.trade.repository.TradeRepository;
import com.chatpay.trade.service.wallet.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TradePaymentServiceTest {

    @Mock
    private ChatMessageFindService chatMessageFindService;
    @Mock
    private TradeRepository tradeRepository;
    @Mock
    private WalletService walletService;
    @Mock
    private MessageResolver messages;

    private TradePaymentService tradePaymentService;

    private final User buyer = Fixture.createUser(1L, "buyer-1");
    private final Item item = Fixture.createItem(1L, "item-1", "테스트 상품", 10000L);
    private final ChatRoom chatRoom = Fixture.createChatRoom(1L, buyer, item);

    private TradePaymentService newTradePaymentService() {
        return new TradePaymentService(chatMessageFindService, tradeRepository, walletService, messages);
    }

    @Test
    @DisplayName("chatRoomId不一致")
    void deniesPayTradeWhenChatRoomIdMismatches() {
        // given
        tradePaymentService = newTradePaymentService();

        // when
        PaymentResponse response = tradePaymentService.payTrade(chatRoom.getId(), 1L, chatRoom.getId() + 1, buyer.getId());

        // then
        assertThat(response).isInstanceOf(PaymentResponse.ChatRoomAccessDenied.class);
        verifyNoInteractions(chatMessageFindService, tradeRepository, walletService);
    }

    @Test
    @DisplayName("決済リクエストメッセージ未存在")
    void paymentNotFoundWhenMessageMissing() {
        // given
        given(chatMessageFindService.findChatMessageById(1L)).willReturn(Optional.empty());
        tradePaymentService = newTradePaymentService();

        // when
        PaymentResponse response = tradePaymentService.payTrade(chatRoom.getId(), 1L, chatRoom.getId(), buyer.getId());

        // then
        assertThat(response).isInstanceOf(PaymentResponse.PaymentNotFound.class);
        verifyNoInteractions(tradeRepository, walletService);
    }

    @Test
    @DisplayName("取引が紐づかないメッセージ")
    void paymentNotFoundWhenTradeNotLinked() {
        // given
        ChatMessage message = Fixture.createChatMessage(1L, chatRoom, null, "결제 요청", MessageType.PAYMENT_REQUEST);
        given(chatMessageFindService.findChatMessageById(1L)).willReturn(Optional.of(message));
        given(tradeRepository.findByChatMessageId(1L)).willReturn(Optional.empty());
        tradePaymentService = newTradePaymentService();

        // when
        PaymentResponse response = tradePaymentService.payTrade(chatRoom.getId(), 1L, chatRoom.getId(), buyer.getId());

        // then
        assertThat(response).isInstanceOf(PaymentResponse.PaymentNotFound.class);
        verifyNoInteractions(walletService);
    }

    @Test
    @DisplayName("ウォレット未存在")
    void walletNotFoundOnPayTrade() {
        // given
        ChatMessage message = Fixture.createChatMessage(1L, chatRoom, null, "결제 요청", MessageType.PAYMENT_REQUEST);
        Trade trade = Fixture.createTrade(1L, buyer, item, item.getName(), item.getPrice(), chatRoom, message);
        given(chatMessageFindService.findChatMessageById(1L)).willReturn(Optional.of(message));
        given(tradeRepository.findByChatMessageId(1L)).willReturn(Optional.of(trade));
        given(walletService.findWalletById(buyer.getId())).willReturn(Optional.empty());
        tradePaymentService = newTradePaymentService();

        // when
        PaymentResponse response = tradePaymentService.payTrade(chatRoom.getId(), 1L, chatRoom.getId(), buyer.getId());

        // then
        assertThat(response).isInstanceOf(PaymentResponse.WalletNotFound.class);
        verify(walletService, never()).debit(any(), anyLong());
        verify(walletService, never()).recordPayment(any(), any(), anyLong());
    }

    @Test
    @DisplayName("決済成功時の処理順序(残高差引→ステータス変更→履歴記録)")
    void paysSuccessfullyInCorrectOrder() {
        // given
        ChatMessage message = Fixture.createChatMessage(1L, chatRoom, null, "결제 요청", MessageType.PAYMENT_REQUEST);
        Trade trade = Fixture.createTrade(1L, buyer, item, item.getName(), item.getPrice(), chatRoom, message);
        Wallet wallet = Fixture.createWallet(buyer, 20000L);
        given(chatMessageFindService.findChatMessageById(1L)).willReturn(Optional.of(message));
        given(tradeRepository.findByChatMessageId(1L)).willReturn(Optional.of(trade));
        given(walletService.findWalletById(buyer.getId())).willReturn(Optional.of(wallet));
        tradePaymentService = newTradePaymentService();

        // when
        PaymentResponse response = tradePaymentService.payTrade(chatRoom.getId(), 1L, chatRoom.getId(), buyer.getId());

        // then
        assertThat(response).isInstanceOfSatisfying(PaymentResponse.PaymentSuccess.class,
                success -> {
                    assertThat(success.chatMessageId()).isEqualTo(1L);
                    assertThat(success.tradeStatus()).isEqualTo(TradeStatus.PAID);
                });
        assertThat(trade.getTradeStatus()).isEqualTo(TradeStatus.PAID);

        // wallet.pay()/flush()는 이제 WalletService 내부(debit/recordPayment)로 옮겨져서
        // 여기(TradePaymentService 테스트)에서는 "그 순서로 위임했는가"만 확인 — 실제 데드락 회피는
        // WalletServiceTest.recordPayment 검증 + TradeFlowIntegrationTest가 담당.
        InOrder order = inOrder(walletService);
        order.verify(walletService).debit(wallet, 10000L);
        order.verify(walletService).recordPayment(wallet, trade, 10000L);
    }

    @Test
    @DisplayName("残高不足")
    void insufficientBalanceHasNoSideEffects() {
        // given
        ChatMessage message = Fixture.createChatMessage(1L, chatRoom, null, "결제 요청", MessageType.PAYMENT_REQUEST);
        Trade trade = Fixture.createTrade(1L, buyer, item, item.getName(), item.getPrice(), chatRoom, message);
        Wallet wallet = Fixture.createWallet(buyer, 0L);
        given(chatMessageFindService.findChatMessageById(1L)).willReturn(Optional.of(message));
        given(tradeRepository.findByChatMessageId(1L)).willReturn(Optional.of(trade));
        given(walletService.findWalletById(buyer.getId())).willReturn(Optional.of(wallet));
        tradePaymentService = newTradePaymentService();

        // when
        PaymentResponse response = tradePaymentService.payTrade(chatRoom.getId(), 1L, chatRoom.getId(), buyer.getId());

        // then
        assertThat(response).isInstanceOf(PaymentResponse.InsufficientBalance.class);
        assertThat(wallet.getBalance()).isZero();
        assertThat(trade.getTradeStatus()).isEqualTo(TradeStatus.PENDING);
        verify(walletService, never()).debit(any(), anyLong());
        verify(walletService, never()).recordPayment(any(), any(), anyLong());
    }

    @Test
    @DisplayName("決済済み取引の再決済防止")
    void alreadyProcessedTradeIsNotChargedAgain() {
        // given
        ChatMessage message = Fixture.createChatMessage(1L, chatRoom, null, "결제 요청", MessageType.PAYMENT_REQUEST);
        Trade trade = Fixture.createTradeWithStatus(1L, buyer, item, item.getName(), item.getPrice(), chatRoom, message, TradeStatus.PAID);
        given(chatMessageFindService.findChatMessageById(1L)).willReturn(Optional.of(message));
        given(tradeRepository.findByChatMessageId(1L)).willReturn(Optional.of(trade));
        tradePaymentService = newTradePaymentService();

        // when
        PaymentResponse response = tradePaymentService.payTrade(chatRoom.getId(), 1L, chatRoom.getId(), buyer.getId());

        // then
        assertThat(response).isInstanceOf(PaymentResponse.PaymentAlreadyProcessed.class);
        verifyNoInteractions(walletService);
    }
}
