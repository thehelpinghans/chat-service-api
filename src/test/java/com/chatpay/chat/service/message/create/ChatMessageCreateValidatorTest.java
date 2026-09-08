package com.chatpay.chat.service.message.create;

import com.chatpay.Fixture;
import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.dto.message.SendMessageResponse;
import com.chatpay.common.domain.Item;
import com.chatpay.common.domain.User;
import com.chatpay.common.domain.UserStatus;
import com.chatpay.common.message.MessageResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ChatMessageCreateValidatorTest {

    @Mock
    private MessageResolver messages;

    private final User buyer = Fixture.createUser(1L, "buyer-1");
    private final Item item = Fixture.createItem(1L, "item-1", "테스트 상품", 10000L);
    private final ChatRoom chatRoom = Fixture.createChatRoom(1L, buyer, item);

    @Test
    @DisplayName("chatRoomId一致確認")
    void passesWhenChatRoomIdMatches() {
        // when
        ChatMessageCreateValidator.Check result = ChatMessageCreateValidator.checkChatRoomAccess(1L, 1L, messages);

        // then
        assertThat(result).isInstanceOf(ChatMessageCreateValidator.Check.Pass.class);
    }

    @Test
    @DisplayName("chatRoomId不一致")
    void deniesWhenChatRoomIdMismatches() {
        // when
        ChatMessageCreateValidator.Check result = ChatMessageCreateValidator.checkChatRoomAccess(1L, 2L, messages);

        // then
        assertThat(result).isInstanceOfSatisfying(ChatMessageCreateValidator.Check.Fail.class,
                fail -> assertThat(fail.response()).isInstanceOf(SendMessageResponse.ChatRoomAccessDenied.class));
    }

    @Test
    @DisplayName("トークンにchatRoomId未指定")
    void deniesWhenTokenChatRoomIdIsNull() {
        // when
        ChatMessageCreateValidator.Check result = ChatMessageCreateValidator.checkChatRoomAccess(1L, null, messages);

        // then
        assertThat(result).isInstanceOfSatisfying(ChatMessageCreateValidator.Check.Fail.class,
                fail -> assertThat(fail.response()).isInstanceOf(SendMessageResponse.ChatRoomAccessDenied.class));
    }

    @Test
    @DisplayName("チャットルーム未存在")
    void notFoundWhenChatRoomIsNull() {
        // when
        ChatMessageCreateValidator.Check result = ChatMessageCreateValidator.checkChatRoomExists(null, messages);

        // then
        assertThat(result).isInstanceOfSatisfying(ChatMessageCreateValidator.Check.Fail.class,
                fail -> assertThat(fail.response()).isInstanceOf(SendMessageResponse.ChatRoomNotFound.class));
    }

    @Test
    @DisplayName("チャットルーム存在確認")
    void passesWhenChatRoomExists() {
        // when
        ChatMessageCreateValidator.Check result = ChatMessageCreateValidator.checkChatRoomExists(chatRoom, messages);

        // then
        assertThat(result).isInstanceOf(ChatMessageCreateValidator.Check.Pass.class);
    }

    @Test
    @DisplayName("ユーザー未存在")
    void notFoundWhenUserIsNull() {
        // when
        ChatMessageCreateValidator.Check result = ChatMessageCreateValidator.checkActiveUser(null, messages);

        // then
        assertThat(result).isInstanceOfSatisfying(ChatMessageCreateValidator.Check.Fail.class,
                fail -> assertThat(fail.response()).isInstanceOf(SendMessageResponse.UserNotFound.class));
    }

    @Test
    @DisplayName("停止中ユーザー")
    void deniesWhenUserIsSuspended() {
        // given
        ReflectionTestUtils.setField(buyer, "status", UserStatus.SUSPENDED);

        // when
        ChatMessageCreateValidator.Check result = ChatMessageCreateValidator.checkActiveUser(buyer, messages);

        // then
        assertThat(result).isInstanceOfSatisfying(ChatMessageCreateValidator.Check.Fail.class,
                fail -> assertThat(fail.response()).isInstanceOf(SendMessageResponse.UserSuspended.class));
        ReflectionTestUtils.setField(buyer, "status", UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("有効ユーザー確認")
    void passesForActiveUser() {
        // when
        ChatMessageCreateValidator.Check result = ChatMessageCreateValidator.checkActiveUser(buyer, messages);

        // then
        assertThat(result).isInstanceOf(ChatMessageCreateValidator.Check.Pass.class);
    }
}
