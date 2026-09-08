package com.chatpay.chat.service.message.find;

import com.chatpay.Fixture;
import com.chatpay.chat.domain.ChatMessage;
import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.domain.MessageType;
import com.chatpay.chat.dto.message.ChatMessageResponse;
import com.chatpay.chat.dto.message.FindMessagesAfterResponse;
import com.chatpay.chat.dto.message.FindMessagesResponse;
import com.chatpay.chat.repository.ChatMessageRepository;
import com.chatpay.common.domain.Item;
import com.chatpay.common.domain.User;
import com.chatpay.common.message.MessageResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ChatMessageFindServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private MessageResolver messages;

    private ChatMessageFindService chatMessageFindService;

    private final User buyer = Fixture.createUser(1L, "buyer-1");
    private final Item item = Fixture.createItem(1L, "item-1", "테스트 상품", 10000L);
    private final ChatRoom chatRoom = Fixture.createChatRoom(1L, buyer, item);

    private ChatMessageFindService newChatMessageFindService() {
        return new ChatMessageFindService(chatMessageRepository, messages);
    }

    @Test
    @DisplayName("chatRoomId不一致(一覧取得)")
    void deniesFindMessagesWhenChatRoomIdMismatches() {
        // given
        chatMessageFindService = newChatMessageFindService();

        // when
        FindMessagesResponse response = chatMessageFindService.findMessages(chatRoom.getId(), chatRoom.getId() + 1, null, 20);

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
        chatMessageFindService = newChatMessageFindService();

        // when
        FindMessagesResponse response = chatMessageFindService.findMessages(chatRoom.getId(), chatRoom.getId(), null, 20);

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
        chatMessageFindService = newChatMessageFindService();

        // when
        FindMessagesResponse response = chatMessageFindService.findMessages(chatRoom.getId(), chatRoom.getId(), 30L, 20);

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
        chatMessageFindService = newChatMessageFindService();

        // when
        FindMessagesResponse response = chatMessageFindService.findMessages(chatRoom.getId(), chatRoom.getId(), null, 20);

        // then
        List<ChatMessageResponse> result = ((FindMessagesResponse.Found) response).messageList();
        assertThat(result.getFirst().userId()).isNull();
    }

    @Test
    @DisplayName("chatRoomId不一致(再接続キャッチアップ)")
    void deniesFindMessagesAfterWhenChatRoomIdMismatches() {
        // given
        chatMessageFindService = newChatMessageFindService();

        // when
        FindMessagesAfterResponse response = chatMessageFindService.findMessagesAfter(chatRoom.getId(), chatRoom.getId() + 1, 0L, 20);

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
        chatMessageFindService = newChatMessageFindService();

        // when
        FindMessagesAfterResponse response = chatMessageFindService.findMessagesAfter(chatRoom.getId(), chatRoom.getId(), 30L, 20);

        // then
        List<ChatMessageResponse> result = ((FindMessagesAfterResponse.Found) response).messageList();
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(31L);
    }

    @Test
    @DisplayName("メッセージ単件取得")
    void findsChatMessageById() {
        // given
        ChatMessage message = Fixture.createChatMessage(1L, chatRoom, buyer, "메시지", MessageType.TEXT);
        given(chatMessageRepository.findById(1L)).willReturn(Optional.of(message));
        chatMessageFindService = newChatMessageFindService();

        // when
        Optional<ChatMessage> result = chatMessageFindService.findChatMessageById(1L);

        // then
        assertThat(result).contains(message);
    }
}
