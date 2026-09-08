package com.chatpay.trade.service.wallet;

import com.chatpay.Fixture;
import com.chatpay.common.domain.Item;
import com.chatpay.common.domain.User;
import com.chatpay.trade.domain.Trade;
import com.chatpay.trade.domain.TransactionType;
import com.chatpay.trade.domain.Wallet;
import com.chatpay.trade.domain.WalletTransaction;
import com.chatpay.trade.repository.WalletRepository;
import com.chatpay.trade.repository.WalletTransactionRepository;
import com.chatpay.chat.domain.ChatMessage;
import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.domain.MessageType;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    private WalletService walletService;

    private final User buyer = Fixture.createUser(1L, "buyer-1");
    private final Item item = Fixture.createItem(1L, "item-1", "테스트 상품", 10000L);
    private final ChatRoom chatRoom = Fixture.createChatRoom(1L, buyer, item);

    private WalletService newWalletService() {
        return new WalletService(walletRepository, walletTransactionRepository);
    }

    @Test
    @DisplayName("ウォレット単件取得")
    void findsWalletById() {
        // given
        Wallet wallet = Fixture.createWallet(buyer, 10000L);
        given(walletRepository.findById(buyer.getId())).willReturn(Optional.of(wallet));
        walletService = newWalletService();

        // when
        Optional<Wallet> result = walletService.findWalletById(buyer.getId());

        // then
        assertThat(result).contains(wallet);
    }

    @Test
    @DisplayName("ウォレット未存在")
    void returnsEmptyWhenWalletNotFound() {
        // given
        given(walletRepository.findById(buyer.getId())).willReturn(Optional.empty());
        walletService = newWalletService();

        // when
        Optional<Wallet> result = walletService.findWalletById(buyer.getId());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("ウォレット新規生成")
    void createsWalletForUser() {
        // given
        given(walletRepository.save(any(Wallet.class))).willAnswer(invocation -> invocation.getArgument(0));
        walletService = newWalletService();

        // when
        Wallet result = walletService.createWallet(buyer);

        // then
        assertThat(result.getUser()).isEqualTo(buyer);
        ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(buyer);
    }

    @Test
    @DisplayName("残高差引")
    void debitsWalletBalance() {
        // given
        Wallet wallet = Fixture.createWallet(buyer, 10000L);
        walletService = newWalletService();

        // when
        walletService.debit(wallet, 4000L);

        // then
        assertThat(wallet.getBalance()).isEqualTo(6000L);
    }

    @Test
    @DisplayName("決済履歴の記録(flush→保存の順序)")
    void recordsPaymentTransaction() {
        // given
        Wallet wallet = Fixture.createWallet(buyer, 0L);
        ChatMessage message = Fixture.createChatMessage(1L, chatRoom, null, "결제 요청", MessageType.PAYMENT_REQUEST);
        Trade trade = Fixture.createTrade(1L, buyer, item, item.getName(), item.getPrice(), chatRoom, message);
        walletService = newWalletService();

        // when
        walletService.recordPayment(wallet, trade, 10000L);

        // then
        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getWallet()).isEqualTo(wallet);
        assertThat(captor.getValue().getTrade()).isEqualTo(trade);
        assertThat(captor.getValue().getAmount()).isEqualTo(-10000L);
        assertThat(captor.getValue().getType()).isEqualTo(TransactionType.PAYMENT);

        // デッドロック回避の要 — flushがINSERTより先に呼ばれる必要がある
        InOrder order = inOrder(walletRepository, walletTransactionRepository);
        order.verify(walletRepository).flush();
        order.verify(walletTransactionRepository).save(any(WalletTransaction.class));
    }
}
