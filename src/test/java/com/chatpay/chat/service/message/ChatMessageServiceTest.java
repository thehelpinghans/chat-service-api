package com.chatpay.chat.service.message;

import com.chatpay.Fixture;
import com.chatpay.chat.domain.ChatMessage;
import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.domain.MessageType;
import com.chatpay.chat.dto.message.ChatMessageRequest;
import com.chatpay.chat.dto.message.ChatMessageResponse;
import com.chatpay.chat.dto.message.FindMessagesAfterResponse;
import com.chatpay.chat.dto.message.FindMessagesResponse;
import com.chatpay.chat.dto.message.SendMessageResponse;
import com.chatpay.chat.repository.ChatMessageRepository;
import com.chatpay.chat.repository.ChatRoomRepository;
import com.chatpay.common.domain.Item;
import com.chatpay.common.domain.User;
import com.chatpay.common.domain.UserStatus;
import com.chatpay.common.message.MessageResolver;
import com.chatpay.common.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private MessageResolver messages;

    private ChatMessageService chatMessageService;

    private final User buyer = Fixture.createUser(1L, "buyer-1");
    private final Item item = Fixture.createItem(1L, "item-1", "테스트 상품", 10000L);
    private final ChatRoom chatRoom = Fixture.createChatRoom(1L, buyer, item);

    private ChatMessageService newChatMessageService() {
        return new ChatMessageService(userRepository, chatRoomRepository, chatMessageRepository, messages);
    }

    @Test
    @DisplayName("chatRoomId不一致")
    void deniesCreateMessageWhenChatRoomIdMismatches() {
        // given
        given(messages.get(anyString())).willReturn("access denied");
        chatMessageService = newChatMessageService();

        // when
        SendMessageResponse response = chatMessageService.createMessage(
                chatRoom.getId(), chatRoom.getId() + 1, buyer.getId(), new ChatMessageRequest("안녕하세요"));

        // then
        assertThat(response).isInstanceOf(SendMessageResponse.ChatRoomAccessDenied.class);
        verifyNoInteractions(chatRoomRepository);
    }

    @Test
    @DisplayName("チャットルーム未存在")
    void chatRoomNotFoundOnCreateMessage() {
        // given
        given(messages.get(anyString())).willReturn("chatroom not found");
        given(chatRoomRepository.findById(chatRoom.getId())).willReturn(Optional.empty());
        chatMessageService = newChatMessageService();

        // when
        SendMessageResponse response = chatMessageService.createMessage(
                chatRoom.getId(), chatRoom.getId(), buyer.getId(), new ChatMessageRequest("안녕하세요"));

        // then
        assertThat(response).isInstanceOf(SendMessageResponse.ChatRoomNotFound.class);
    }

    @Test
    @DisplayName("ユーザー未存在")
    void userNotFoundOnCreateMessage() {
        // given
        given(messages.get(anyString())).willReturn("user not found");
        given(chatRoomRepository.findById(chatRoom.getId())).willReturn(Optional.of(chatRoom));
        given(userRepository.findById(buyer.getId())).willReturn(Optional.empty());
        chatMessageService = newChatMessageService();

        // when
        SendMessageResponse response = chatMessageService.createMessage(
                chatRoom.getId(), chatRoom.getId(), buyer.getId(), new ChatMessageRequest("안녕하세요"));

        // then
        assertThat(response).isInstanceOf(SendMessageResponse.UserNotFound.class);
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("停止中ユーザー")
    void suspendedUserCannotSendMessage() {
        // given
        given(messages.get(anyString())).willReturn("user suspended");
        given(chatRoomRepository.findById(chatRoom.getId())).willReturn(Optional.of(chatRoom));
        ReflectionTestUtils.setField(buyer, "status", UserStatus.SUSPENDED);
        given(userRepository.findById(buyer.getId())).willReturn(Optional.of(buyer));
        chatMessageService = newChatMessageService();

        // when
        SendMessageResponse response = chatMessageService.createMessage(
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
        given(chatRoomRepository.findById(chatRoom.getId())).willReturn(Optional.of(chatRoom));
        given(chatMessageRepository.save(any(ChatMessage.class)))
                .willAnswer(invocation -> {
                    ChatMessage saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 100L);
                    return saved;
                });
        chatMessageService = newChatMessageService();

        // when
        SendMessageResponse response = chatMessageService.createMessage(
                chatRoom.getId(), chatRoom.getId(), null, new ChatMessageRequest("결제 확인 부탁드립니다"));

        // then
        assertThat(response).isInstanceOfSatisfying(SendMessageResponse.Sent.class,
                sent -> assertThat(sent.message().userId()).isNull());
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("有効ユーザーによるメッセージ送信")
    void activeUserCanSendMessage() {
        // given
        given(chatRoomRepository.findById(chatRoom.getId())).willReturn(Optional.of(chatRoom));
        given(userRepository.findById(buyer.getId())).willReturn(Optional.of(buyer));
        given(chatMessageRepository.save(any(ChatMessage.class)))
                .willAnswer(invocation -> {
                    ChatMessage saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 100L);
                    return saved;
                });
        chatMessageService = newChatMessageService();

        // when
        SendMessageResponse response = chatMessageService.createMessage(
                chatRoom.getId(), chatRoom.getId(), buyer.getId(), new ChatMessageRequest("안녕하세요"));

        // then
        assertThat(response).isInstanceOfSatisfying(SendMessageResponse.Sent.class, sent -> {
            assertThat(sent.message().userId()).isEqualTo(buyer.getId());
            assertThat(sent.message().content()).isEqualTo("안녕하세요");
        });
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getMessageType()).isEqualTo(MessageType.TEXT);
    }

    @Test
    @DisplayName("chatRoomId不一致")
    void deniesFindMessagesWhenChatRoomIdMismatches() {
        // given
        given(messages.get(anyString())).willReturn("access denied");
        chatMessageService = newChatMessageService();

        // when
        FindMessagesResponse response = chatMessageService.findMessages(chatRoom.getId(), chatRoom.getId() + 1, null, 20);

        // then
        assertThat(response).isInstanceOf(FindMessagesResponse.ChatRoomAccessDenied.class);
        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    @DisplayName("最新メッセージ降順取得")
    void findsLatestMessagesDescending() {
        // given
        ChatMessage message = Fixture.createChatMessage(50L, chatRoom, buyer, "최신 메시지", MessageType.TEXT);
        given(chatMessageRepository.findByChatRoomIdOrderByIdDesc(eq(chatRoom.getId()), any(Limit.class)))
                .willReturn(List.of(message));
        chatMessageService = newChatMessageService();

        // when
        FindMessagesResponse response = chatMessageService.findMessages(chatRoom.getId(), chatRoom.getId(), null, 20);

        // then
        List<ChatMessageResponse> result = ((FindMessagesResponse.Found) response).messageList();
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(50L);
        verify(chatMessageRepository, never()).findByChatRoomIdAndIdLessThanOrderByIdDesc(any(), any(), any());
    }

    @Test
    @DisplayName("lastMessageId指定取得")
    void findsMessagesBeforeLastMessageId() {
        // given
        ChatMessage message = Fixture.createChatMessage(29L, chatRoom, buyer, "이전 메시지", MessageType.TEXT);
        given(chatMessageRepository.findByChatRoomIdAndIdLessThanOrderByIdDesc(eq(chatRoom.getId()), eq(30L), any(Limit.class)))
                .willReturn(List.of(message));
        chatMessageService = newChatMessageService();

        // when
        FindMessagesResponse response = chatMessageService.findMessages(chatRoom.getId(), chatRoom.getId(), 30L, 20);

        // then
        List<ChatMessageResponse> result = ((FindMessagesResponse.Found) response).messageList();
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(29L);
    }

    @Test
    @DisplayName("テナント発信メッセージのuserId表現")
    void tenantMessageHasNullUserIdInResponse() {
        // given
        ChatMessage tenantMessage = Fixture.createChatMessage(1L, chatRoom, null, "결제 요청", MessageType.PAYMENT_REQUEST);
        given(chatMessageRepository.findByChatRoomIdOrderByIdDesc(eq(chatRoom.getId()), any(Limit.class)))
                .willReturn(List.of(tenantMessage));
        chatMessageService = newChatMessageService();

        // when
        FindMessagesResponse response = chatMessageService.findMessages(chatRoom.getId(), chatRoom.getId(), null, 20);

        // then
        List<ChatMessageResponse> result = ((FindMessagesResponse.Found) response).messageList();
        assertThat(result.getFirst().userId()).isNull();
    }

    @Test
    @DisplayName("chatRoomId不一致")
    void deniesFindMessagesAfterWhenChatRoomIdMismatches() {
        // given
        given(messages.get(anyString())).willReturn("access denied");
        chatMessageService = newChatMessageService();

        // when
        FindMessagesAfterResponse response = chatMessageService.findMessagesAfter(chatRoom.getId(), chatRoom.getId() + 1, 0L, 20);

        // then
        assertThat(response).isInstanceOf(FindMessagesAfterResponse.ChatRoomAccessDenied.class);
        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    @DisplayName("再接続キャッチアップ取得")
    void findsMessagesAfterIdAscending() {
        // given
        ChatMessage message = Fixture.createChatMessage(31L, chatRoom, buyer, "새 메시지", MessageType.TEXT);
        given(chatMessageRepository.findByChatRoomIdAndIdGreaterThanOrderByIdAsc(eq(chatRoom.getId()), eq(30L), any(Limit.class)))
                .willReturn(List.of(message));
        chatMessageService = newChatMessageService();

        // when
        FindMessagesAfterResponse response = chatMessageService.findMessagesAfter(chatRoom.getId(), chatRoom.getId(), 30L, 20);

        // then
        List<ChatMessageResponse> result = ((FindMessagesAfterResponse.Found) response).messageList();
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(31L);
    }

    @Test
    @DisplayName("決済リクエストメッセージは発信者なしで生成")
    void createsPaymentRequestMessageWithoutSender() {
        // given
        given(chatMessageRepository.save(any(ChatMessage.class))).willAnswer(invocation -> invocation.getArgument(0));
        chatMessageService = newChatMessageService();

        // when
        ChatMessage result = chatMessageService.createPaymentRequestMessage(chatRoom);

        // then
        assertThat(result.getMessageType()).isEqualTo(MessageType.PAYMENT_REQUEST);
        assertThat(result.getUser()).isNull();
        assertThat(result.getChatRoom()).isEqualTo(chatRoom);
    }

    @Test
    @DisplayName("メッセージ単件取得")
    void findsChatMessageById() {
        // given
        ChatMessage message = Fixture.createChatMessage(1L, chatRoom, buyer, "메시지", MessageType.TEXT);
        given(chatMessageRepository.findById(1L)).willReturn(Optional.of(message));
        chatMessageService = newChatMessageService();

        // when
        Optional<ChatMessage> result = chatMessageService.findChatMessageById(1L);

        // then
        assertThat(result).contains(message);
    }
}
