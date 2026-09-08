package com.chatpay.chat.service.message.create;

import com.chatpay.Fixture;
import com.chatpay.chat.domain.ChatMessage;
import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.domain.MessageType;
import com.chatpay.chat.dto.message.ChatMessageRequest;
import com.chatpay.chat.dto.message.SendMessageResponse;
import com.chatpay.chat.repository.ChatMessageRepository;
import com.chatpay.chat.service.room.ChatRoomService;
import com.chatpay.common.domain.Item;
import com.chatpay.common.domain.User;
import com.chatpay.common.domain.UserStatus;
import com.chatpay.common.message.MessageResolver;
import com.chatpay.common.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ChatMessageCreateServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private ChatRoomService chatRoomService;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private MessageResolver messages;

    private ChatMessageCreateService chatMessageCreateService;

    private final User buyer = Fixture.createUser(1L, "buyer-1");
    private final Item item = Fixture.createItem(1L, "item-1", "테스트 상품", 10000L);
    private final ChatRoom chatRoom = Fixture.createChatRoom(1L, buyer, item);

    private ChatMessageCreateService newChatMessageCreateService() {
        return new ChatMessageCreateService(userService, chatRoomService, chatMessageRepository, messages);
    }

    @Test
    @DisplayName("chatRoomId不一致")
    void deniesCreateMessageWhenChatRoomIdMismatches() {
        // given
        chatMessageCreateService = newChatMessageCreateService();

        // when
        SendMessageResponse response = chatMessageCreateService.createMessage(
                chatRoom.getId(), chatRoom.getId() + 1, buyer.getId(), new ChatMessageRequest("안녕하세요"));

        // then
        assertThat(response).isInstanceOf(SendMessageResponse.ChatRoomAccessDenied.class);
        verifyNoInteractions(chatRoomService);
    }

    @Test
    @DisplayName("チャットルーム未存在")
    void chatRoomNotFoundOnCreateMessage() {
        // given
        given(chatRoomService.findChatRoomById(chatRoom.getId())).willReturn(Optional.empty());
        chatMessageCreateService = newChatMessageCreateService();

        // when
        SendMessageResponse response = chatMessageCreateService.createMessage(
                chatRoom.getId(), chatRoom.getId(), buyer.getId(), new ChatMessageRequest("안녕하세요"));

        // then
        assertThat(response).isInstanceOf(SendMessageResponse.ChatRoomNotFound.class);
    }

    @Test
    @DisplayName("ユーザー未存在")
    void userNotFoundOnCreateMessage() {
        // given
        given(chatRoomService.findChatRoomById(chatRoom.getId())).willReturn(Optional.of(chatRoom));
        given(userService.findUserById(buyer.getId())).willReturn(Optional.empty());
        chatMessageCreateService = newChatMessageCreateService();

        // when
        SendMessageResponse response = chatMessageCreateService.createMessage(
                chatRoom.getId(), chatRoom.getId(), buyer.getId(), new ChatMessageRequest("안녕하세요"));

        // then
        assertThat(response).isInstanceOf(SendMessageResponse.UserNotFound.class);
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("停止中ユーザー")
    void suspendedUserCannotSendMessage() {
        // given
        given(chatRoomService.findChatRoomById(chatRoom.getId())).willReturn(Optional.of(chatRoom));
        ReflectionTestUtils.setField(buyer, "status", UserStatus.SUSPENDED);
        given(userService.findUserById(buyer.getId())).willReturn(Optional.of(buyer));
        chatMessageCreateService = newChatMessageCreateService();

        // when
        SendMessageResponse response = chatMessageCreateService.createMessage(
                chatRoom.getId(), chatRoom.getId(), buyer.getId(), new ChatMessageRequest("안녕하세요"));

        // then
        assertThat(response).isInstanceOf(SendMessageResponse.UserSuspended.class);
        verify(chatMessageRepository, never()).save(any());
        ReflectionTestUtils.setField(buyer, "status", UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("テナント発信メッセージ(userId未指定)")
    void tenantCanSendMessageWithoutUser() {
        // given
        given(chatRoomService.findChatRoomById(chatRoom.getId())).willReturn(Optional.of(chatRoom));
        given(chatMessageRepository.save(any(ChatMessage.class)))
                .willAnswer(invocation -> {
                    ChatMessage saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 100L);
                    return saved;
                });
        chatMessageCreateService = newChatMessageCreateService();

        // when
        SendMessageResponse response = chatMessageCreateService.createMessage(
                chatRoom.getId(), chatRoom.getId(), null, new ChatMessageRequest("결제 확인 부탁드립니다"));

        // then
        assertThat(response).isInstanceOfSatisfying(SendMessageResponse.Sent.class,
                sent -> assertThat(sent.message().userId()).isNull());
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("有効ユーザーによるメッセージ送信")
    void activeUserCanSendMessage() {
        // given
        given(chatRoomService.findChatRoomById(chatRoom.getId())).willReturn(Optional.of(chatRoom));
        given(userService.findUserById(buyer.getId())).willReturn(Optional.of(buyer));
        given(chatMessageRepository.save(any(ChatMessage.class)))
                .willAnswer(invocation -> {
                    ChatMessage saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 100L);
                    return saved;
                });
        chatMessageCreateService = newChatMessageCreateService();

        // when
        SendMessageResponse response = chatMessageCreateService.createMessage(
                chatRoom.getId(), chatRoom.getId(), buyer.getId(), new ChatMessageRequest("안녕하세요"));

        // then
        assertThat(response).isInstanceOfSatisfying(SendMessageResponse.Sent.class, sent -> {
            assertThat(sent.message().userId()).isEqualTo(buyer.getId());
            assertThat(sent.message().content()).isEqualTo("안녕하세요");
        });
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getMessageType()).isEqualTo(MessageType.TEXT);
        assertThat(captor.getValue().getUser()).isEqualTo(buyer);
    }

    @Test
    @DisplayName("決済リクエストメッセージは発信者なしで生成")
    void createsPaymentRequestMessageWithoutSender() {
        // given
        given(chatMessageRepository.save(any(ChatMessage.class))).willAnswer(invocation -> invocation.getArgument(0));
        chatMessageCreateService = newChatMessageCreateService();

        // when
        ChatMessage result = chatMessageCreateService.createPaymentRequestMessage(chatRoom);

        // then
        assertThat(result.getMessageType()).isEqualTo(MessageType.PAYMENT_REQUEST);
        assertThat(result.getUser()).isNull();
        assertThat(result.getChatRoom()).isEqualTo(chatRoom);
    }
}
