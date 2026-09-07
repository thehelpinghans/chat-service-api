package com.chatpay.chat.service.room;

import com.chatpay.Fixture;
import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.dto.room.ChatRoomCreateRequest;
import com.chatpay.chat.dto.room.ChatRoomUpsertResult;
import com.chatpay.chat.repository.ChatRoomRepository;
import com.chatpay.common.domain.Item;
import com.chatpay.common.domain.User;
import com.chatpay.common.service.ItemService;
import com.chatpay.common.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 동시성(유니크 제약 위반/재시도 확정)은 Mockito로 대체 불가 — integration.chat.ChatRoomServiceIntegrationTest 참고
@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private ItemService itemService;
    @Mock
    private ChatRoomRepository chatRoomRepository;

    private ChatRoomService chatRoomService;

    private final User buyer = Fixture.createUser(1L, "buyer-1");
    private final Item item = Fixture.createItem(1L, "item-1", "테스트 상품", 10000L);

    private ChatRoomService newChatRoomService() {
        return new ChatRoomService(userService, itemService, chatRoomRepository);
    }

    @Test
    @DisplayName("既存チャットルームの再利用")
    void reusesExistingChatRoom() {
        // given
        ChatRoom existing = Fixture.createChatRoom(1L, buyer, item);
        given(userService.getOrCreateUser("buyer-1")).willReturn(buyer);
        given(itemService.getOrCreateItem("item-1", "테스트 상품", 10000L)).willReturn(item);
        given(chatRoomRepository.findByUserIdAndItemId(buyer.getId(), item.getId())).willReturn(Optional.of(existing));
        chatRoomService = newChatRoomService();

        // when
        ChatRoomUpsertResult result = chatRoomService.getOrCreateChatRoom(
                new ChatRoomCreateRequest("buyer-1", "item-1", "테스트 상품", 10000L));

        // then
        assertThat(result.created()).isFalse();
        assertThat(result.body().chatRoomId()).isEqualTo(existing.getId());
        verify(chatRoomRepository, never()).save(any());
    }

    @Test
    @DisplayName("チャットルーム新規生成")
    void createsNewChatRoom() {
        // given
        given(userService.getOrCreateUser("buyer-1")).willReturn(buyer);
        given(itemService.getOrCreateItem("item-1", "테스트 상품", 10000L)).willReturn(item);
        given(chatRoomRepository.findByUserIdAndItemId(buyer.getId(), item.getId())).willReturn(Optional.empty());
        ChatRoom created = Fixture.createChatRoom(2L, buyer, item);
        given(chatRoomRepository.save(any(ChatRoom.class))).willReturn(created);
        chatRoomService = newChatRoomService();

        // when
        ChatRoomUpsertResult result = chatRoomService.getOrCreateChatRoom(
                new ChatRoomCreateRequest("buyer-1", "item-1", "테스트 상품", 10000L));

        // then
        assertThat(result.created()).isTrue();
        assertThat(result.body().chatRoomId()).isEqualTo(created.getId());
    }

    @Test
    @DisplayName("外部ID指定チャットルーム取得")
    void findsChatRoomByExternalIds() {
        // given
        ChatRoom chatRoom = Fixture.createChatRoom(1L, buyer, item);
        given(chatRoomRepository.findByUserExternalIdAndItemExternalItemId("buyer-1", "item-1"))
                .willReturn(Optional.of(chatRoom));
        chatRoomService = newChatRoomService();

        // when
        Optional<ChatRoom> result = chatRoomService.findChatRoom("buyer-1", "item-1");

        // then
        assertThat(result).contains(chatRoom);
    }

    @Test
    @DisplayName("チャットルーム単件取得")
    void findsChatRoomById() {
        // given
        ChatRoom chatRoom = Fixture.createChatRoom(1L, buyer, item);
        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(chatRoom));
        chatRoomService = newChatRoomService();

        // when
        Optional<ChatRoom> result = chatRoomService.findChatRoomById(1L);

        // then
        assertThat(result).contains(chatRoom);
    }
}
