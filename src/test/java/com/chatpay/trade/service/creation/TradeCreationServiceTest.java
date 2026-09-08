package com.chatpay.trade.service.creation;

import com.chatpay.Fixture;
import com.chatpay.chat.domain.ChatMessage;
import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.domain.MessageType;
import com.chatpay.chat.service.message.create.ChatMessageCreateService;
import com.chatpay.chat.service.room.ChatRoomService;
import com.chatpay.common.domain.Item;
import com.chatpay.common.domain.User;
import com.chatpay.common.message.MessageResolver;
import com.chatpay.trade.domain.Trade;
import com.chatpay.trade.domain.TradeStatus;
import com.chatpay.trade.dto.TradeCreateResponse;
import com.chatpay.trade.repository.TradeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TradeCreationServiceTest {

    @Mock
    private ChatRoomService chatRoomService;
    @Mock
    private ChatMessageCreateService chatMessageCreateService;
    @Mock
    private TradeRepository tradeRepository;
    @Mock
    private MessageResolver messages;

    private TradeCreationService tradeCreationService;

    private final User buyer = Fixture.createUser(1L, "buyer-1");
    private final Item item = Fixture.createItem(1L, "item-1", "테스트 상품", 10000L);
    private final ChatRoom chatRoom = Fixture.createChatRoom(1L, buyer, item);

    private TradeCreationService newTradeCreationService() {
        return new TradeCreationService(chatRoomService, chatMessageCreateService, tradeRepository, messages);
    }

    @Test
    @DisplayName("オペレーターによる決済リクエスト生成")
    void sellerCannotCreateTrade() {
        // given
        tradeCreationService = newTradeCreationService();

        // when
        TradeCreateResponse response = tradeCreationService.createTrade(chatRoom.getId(), chatRoom.getId(), 999L);

        // then
        assertThat(response).isInstanceOf(TradeCreateResponse.SellerOnly.class);
        verifyNoInteractions(chatRoomService, chatMessageCreateService, tradeRepository);
    }

    @Test
    @DisplayName("chatRoomId不一致")
    void deniesCreateTradeWhenChatRoomIdMismatches() {
        // given
        tradeCreationService = newTradeCreationService();

        // when
        TradeCreateResponse response = tradeCreationService.createTrade(chatRoom.getId(), chatRoom.getId() + 1, null);

        // then
        assertThat(response).isInstanceOf(TradeCreateResponse.ChatRoomAccessDenied.class);
        verifyNoInteractions(chatRoomService);
    }

    @Test
    @DisplayName("チャットルーム未存在")
    void chatRoomNotFoundOnCreateTrade() {
        // given
        given(chatRoomService.findChatRoomById(chatRoom.getId())).willReturn(Optional.empty());
        tradeCreationService = newTradeCreationService();

        // when
        TradeCreateResponse response = tradeCreationService.createTrade(chatRoom.getId(), chatRoom.getId(), null);

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
        tradeCreationService = newTradeCreationService();

        // when
        TradeCreateResponse response = tradeCreationService.createTrade(chatRoom.getId(), chatRoom.getId(), null);

        // then
        assertThat(response).isInstanceOfSatisfying(TradeCreateResponse.Found.class,
                found -> assertThat(found.id()).isEqualTo(existingMessage.getId()));
        verify(chatMessageCreateService, never()).createPaymentRequestMessage(any());
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
        given(chatMessageCreateService.createPaymentRequestMessage(chatRoom)).willReturn(paymentMessage);
        tradeCreationService = newTradeCreationService();

        // when
        TradeCreateResponse response = tradeCreationService.createTrade(chatRoom.getId(), chatRoom.getId(), null);

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
        assertThat(savedTrade.getUser()).isEqualTo(buyer);
        assertThat(savedTrade.getItemName()).isEqualTo(item.getName());
        assertThat(savedTrade.getAmount()).isEqualTo(item.getPrice());
        assertThat(savedTrade.getChatMessage()).isEqualTo(paymentMessage);
    }
}
