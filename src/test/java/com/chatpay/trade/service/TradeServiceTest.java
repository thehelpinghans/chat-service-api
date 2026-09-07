package com.chatpay.trade.service;

import com.chatpay.Fixture;
import com.chatpay.chat.domain.ChatMessage;
import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.domain.MessageType;
import com.chatpay.chat.service.message.ChatMessageService;
import com.chatpay.chat.service.room.ChatRoomService;
import com.chatpay.common.domain.Item;
import com.chatpay.common.domain.User;
import com.chatpay.common.message.MessageResolver;
import com.chatpay.trade.domain.Trade;
import com.chatpay.trade.domain.TradeStatus;
import com.chatpay.trade.domain.Wallet;
import com.chatpay.trade.domain.WalletTransaction;
import com.chatpay.trade.dto.PaymentResponse;
import com.chatpay.trade.dto.TradeCreateResponse;
import com.chatpay.trade.repository.TradeRepository;
import com.chatpay.trade.repository.WalletRepository;
import com.chatpay.trade.repository.WalletTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

// 동시성/DB 제약 검증은 Mockito로 대체 불가 — integration.trade.TradeServiceIntegrationTest 참고
@ExtendWith(MockitoExtension.class)
class TradeServiceTest {

    @Mock
    private ChatRoomService chatRoomService;
    @Mock
    private ChatMessageService chatMessageService;
    @Mock
    private TradeRepository tradeRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private WalletTransactionRepository walletTransactionRepository;
    @Mock
    private MessageResolver messages;

    private TradeService tradeService;

    private final User buyer = Fixture.createUser(1L, "buyer-1");
    private final Item item = Fixture.createItem(1L, "item-1", "테스트 상품", 10000L);
    private final ChatRoom chatRoom = Fixture.createChatRoom(1L, buyer, item);

    private TradeService newTradeService() {
        return new TradeService(chatRoomService, chatMessageService, tradeRepository,
                walletRepository, walletTransactionRepository, messages);
    }

    @Test
    @DisplayName("オペレーターによる決済リクエスト生成")
    void sellerCannotCreateTrade() {
        // given
        given(messages.get(anyString())).willReturn("seller only");
        tradeService = newTradeService();

        // when
        TradeCreateResponse response = tradeService.createTrade(chatRoom.getId(), chatRoom.getId(), 999L);

        // then
        assertThat(response).isInstanceOf(TradeCreateResponse.SellerOnly.class);
        verifyNoInteractions(chatRoomService, chatMessageService, tradeRepository);
    }

    @Test
    @DisplayName("chatRoomId不一致")
    void deniesCreateTradeWhenChatRoomIdMismatches() {
        // given
        given(messages.get(anyString())).willReturn("access denied");
        tradeService = newTradeService();

        // when
        TradeCreateResponse response = tradeService.createTrade(chatRoom.getId(), chatRoom.getId() + 1, null);

        // then
        assertThat(response).isInstanceOf(TradeCreateResponse.ChatRoomAccessDenied.class);
        verifyNoInteractions(chatRoomService);
    }

    @Test
    @DisplayName("チャットルーム未存在")
    void chatRoomNotFoundOnCreateTrade() {
        // given
        given(messages.get(anyString())).willReturn("chatroom not found");
        given(chatRoomService.findChatRoomById(chatRoom.getId())).willReturn(Optional.empty());
        tradeService = newTradeService();

        // when
        TradeCreateResponse response = tradeService.createTrade(chatRoom.getId(), chatRoom.getId(), null);

        // then
        assertThat(response).isInstanceOf(TradeCreateResponse.ChatRoomNotFound.class);
    }

    @Test
    @DisplayName("PENDING取引の重複生成防止")
    void returnsExistingTradeWhenPendingTradeExists() {
        // given
        given(chatRoomService.findChatRoomById(chatRoom.getId())).willReturn(Optional.of(chatRoom));
        ChatMessage existingMessage = Fixture.createChatMessage(10L, chatRoom, null, "결제 요청", MessageType.PAYMENT_REQUEST);
        Trade existingPendingTrade = Fixture.createTrade(1L, buyer, item, item.getName(), item.getPrice(), chatRoom, existingMessage);
        given(tradeRepository.findByChatRoomIdAndTradeStatus(chatRoom.getId(), TradeStatus.PENDING))
                .willReturn(Optional.of(existingPendingTrade));
        tradeService = newTradeService();

        // when
        TradeCreateResponse response = tradeService.createTrade(chatRoom.getId(), chatRoom.getId(), null);

        // then
        assertThat(response).isInstanceOfSatisfying(TradeCreateResponse.Found.class,
                found -> assertThat(found.id()).isEqualTo(existingMessage.getId()));
        verify(chatMessageService, never()).createPaymentRequestMessage(any());
        verify(tradeRepository, never()).save(any());
    }

    @Test
    @DisplayName("PENDING取引の新規生成")
    void createsNewTradeWhenNoPendingTradeExists() {
        // given
        given(chatRoomService.findChatRoomById(chatRoom.getId())).willReturn(Optional.of(chatRoom));
        given(tradeRepository.findByChatRoomIdAndTradeStatus(chatRoom.getId(), TradeStatus.PENDING))
                .willReturn(Optional.empty());
        ChatMessage paymentMessage = Fixture.createChatMessage(20L, chatRoom, null, "결제 요청", MessageType.PAYMENT_REQUEST);
        given(chatMessageService.createPaymentRequestMessage(chatRoom)).willReturn(paymentMessage);
        tradeService = newTradeService();

        // when
        TradeCreateResponse response = tradeService.createTrade(chatRoom.getId(), chatRoom.getId(), null);

        // then
        assertThat(response).isInstanceOfSatisfying(TradeCreateResponse.Created.class,
                created -> {
                    assertThat(created.id()).isEqualTo(paymentMessage.getId());
                    assertThat(created.messageType()).isEqualTo(MessageType.PAYMENT_REQUEST);
                });
        ArgumentCaptor<Trade> tradeCaptor = ArgumentCaptor.forClass(Trade.class);
        verify(tradeRepository).save(tradeCaptor.capture());
        Trade savedTrade = tradeCaptor.getValue();
        assertThat(savedTrade.getTradeStatus()).isEqualTo(TradeStatus.PENDING);
        assertThat(savedTrade.getItemName()).isEqualTo(item.getName());
        assertThat(savedTrade.getAmount()).isEqualTo(item.getPrice());
        assertThat(savedTrade.getChatMessage()).isEqualTo(paymentMessage);
    }

    @Test
    @DisplayName("chatRoomId不一致")
    void deniesPayTradeWhenChatRoomIdMismatches() {
        // given
        given(messages.get(anyString())).willReturn("access denied");
        tradeService = newTradeService();

        // when
        PaymentResponse response = tradeService.payTrade(chatRoom.getId(), 1L, chatRoom.getId() + 1, buyer.getId());

        // then
        assertThat(response).isInstanceOf(PaymentResponse.ChatRoomAccessDenied.class);
        verifyNoInteractions(chatMessageService, tradeRepository, walletRepository);
    }

    @Test
    @DisplayName("決済リクエストメッセージ未存在")
    void paymentNotFoundWhenMessageMissing() {
        // given
        given(messages.get(anyString())).willReturn("not found");
        given(chatMessageService.findChatMessageById(1L)).willReturn(Optional.empty());
        tradeService = newTradeService();

        // when
        PaymentResponse response = tradeService.payTrade(chatRoom.getId(), 1L, chatRoom.getId(), buyer.getId());

        // then
        assertThat(response).isInstanceOf(PaymentResponse.PaymentNotFound.class);
        verifyNoInteractions(tradeRepository, walletRepository);
    }

    @Test
    @DisplayName("取引が紐づかないメッセージ")
    void paymentNotFoundWhenTradeNotLinked() {
        // given
        given(messages.get(anyString())).willReturn("not found");
        ChatMessage message = Fixture.createChatMessage(1L, chatRoom, null, "결제 요청", MessageType.PAYMENT_REQUEST);
        given(chatMessageService.findChatMessageById(1L)).willReturn(Optional.of(message));
        given(tradeRepository.findByChatMessageId(1L)).willReturn(Optional.empty());
        tradeService = newTradeService();

        // when
        PaymentResponse response = tradeService.payTrade(chatRoom.getId(), 1L, chatRoom.getId(), buyer.getId());

        // then
        assertThat(response).isInstanceOf(PaymentResponse.PaymentNotFound.class);
        verifyNoInteractions(walletRepository);
    }

    @Test
    @DisplayName("ウォレット未存在")
    void walletNotFoundOnPayTrade() {
        // given
        given(messages.get(anyString())).willReturn("wallet not found");
        ChatMessage message = Fixture.createChatMessage(1L, chatRoom, null, "결제 요청", MessageType.PAYMENT_REQUEST);
        Trade trade = Fixture.createTrade(1L, buyer, item, item.getName(), item.getPrice(), chatRoom, message);
        given(chatMessageService.findChatMessageById(1L)).willReturn(Optional.of(message));
        given(tradeRepository.findByChatMessageId(1L)).willReturn(Optional.of(trade));
        given(walletRepository.findById(buyer.getId())).willReturn(Optional.empty());
        tradeService = newTradeService();

        // when
        PaymentResponse response = tradeService.payTrade(chatRoom.getId(), 1L, chatRoom.getId(), buyer.getId());

        // then
        assertThat(response).isInstanceOf(PaymentResponse.WalletNotFound.class);
        verify(walletTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("決済成功時の処理順序(残高差引→flush→履歴保存)")
    void paysSuccessfullyInCorrectOrder() {
        // given
        ChatMessage message = Fixture.createChatMessage(1L, chatRoom, null, "결제 요청", MessageType.PAYMENT_REQUEST);
        Trade trade = Fixture.createTrade(1L, buyer, item, item.getName(), item.getPrice(), chatRoom, message);
        Wallet wallet = Fixture.createWallet(buyer, 20000L);
        given(chatMessageService.findChatMessageById(1L)).willReturn(Optional.of(message));
        given(tradeRepository.findByChatMessageId(1L)).willReturn(Optional.of(trade));
        given(walletRepository.findById(buyer.getId())).willReturn(Optional.of(wallet));
        tradeService = newTradeService();

        // when
        PaymentResponse response = tradeService.payTrade(chatRoom.getId(), 1L, chatRoom.getId(), buyer.getId());

        // then
        assertThat(response).isInstanceOfSatisfying(PaymentResponse.PaymentSuccess.class,
                success -> {
                    assertThat(success.chatMessageId()).isEqualTo(1L);
                    assertThat(success.tradeStatus()).isEqualTo(TradeStatus.PAID);
                });
        assertThat(wallet.getBalance()).isEqualTo(10000L);
        assertThat(trade.getTradeStatus()).isEqualTo(TradeStatus.PAID);

        // Mockito는 실제 MySQL 락 동작을 검증할 수 없음 — 여기서 보장하는 건 "코드가 여전히
        // 그 순서로 호출하는가"뿐. 실제 데드락 회피는 integration.trade.TradeServiceIntegrationTest가 검증.
        InOrder order = inOrder(walletRepository, walletTransactionRepository);
        order.verify(walletRepository).flush();
        order.verify(walletTransactionRepository).save(any(WalletTransaction.class));
    }

    @Test
    @DisplayName("残高不足")
    void insufficientBalanceHasNoSideEffects() {
        // given
        given(messages.get(anyString())).willReturn("insufficient balance");
        ChatMessage message = Fixture.createChatMessage(1L, chatRoom, null, "결제 요청", MessageType.PAYMENT_REQUEST);
        Trade trade = Fixture.createTrade(1L, buyer, item, item.getName(), item.getPrice(), chatRoom, message);
        Wallet wallet = Fixture.createWallet(buyer, 0L);
        given(chatMessageService.findChatMessageById(1L)).willReturn(Optional.of(message));
        given(tradeRepository.findByChatMessageId(1L)).willReturn(Optional.of(trade));
        given(walletRepository.findById(buyer.getId())).willReturn(Optional.of(wallet));
        tradeService = newTradeService();

        // when
        PaymentResponse response = tradeService.payTrade(chatRoom.getId(), 1L, chatRoom.getId(), buyer.getId());

        // then
        assertThat(response).isInstanceOf(PaymentResponse.InsufficientBalance.class);
        assertThat(wallet.getBalance()).isZero();
        assertThat(trade.getTradeStatus()).isEqualTo(TradeStatus.PENDING);
        verify(walletTransactionRepository, never()).save(any());
        verify(walletRepository, never()).flush();
    }

    @Test
    @DisplayName("決済済み取引の再決済防止")
    void alreadyProcessedTradeIsNotChargedAgain() {
        // given
        given(messages.get(anyString())).willReturn("already processed");
        ChatMessage message = Fixture.createChatMessage(1L, chatRoom, null, "결제 요청", MessageType.PAYMENT_REQUEST);
        Trade trade = Fixture.createTradeWithStatus(1L, buyer, item, item.getName(), item.getPrice(), chatRoom, message, TradeStatus.PAID);
        given(chatMessageService.findChatMessageById(1L)).willReturn(Optional.of(message));
        given(tradeRepository.findByChatMessageId(1L)).willReturn(Optional.of(trade));
        tradeService = newTradeService();

        // when
        PaymentResponse response = tradeService.payTrade(chatRoom.getId(), 1L, chatRoom.getId(), buyer.getId());

        // then
        assertThat(response).isInstanceOf(PaymentResponse.PaymentAlreadyProcessed.class);
        verifyNoInteractions(walletRepository, walletTransactionRepository);
    }
}
