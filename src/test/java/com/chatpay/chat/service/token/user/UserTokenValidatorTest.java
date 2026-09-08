package com.chatpay.chat.service.token.user;

import com.chatpay.Fixture;
import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.dto.token.IssueUserTokenResponse;
import com.chatpay.common.domain.Item;
import com.chatpay.common.domain.User;
import com.chatpay.common.message.MessageResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class UserTokenValidatorTest {

    @Mock
    private MessageResolver messages;

    private final User buyer = Fixture.createUser(1L, "buyer-1");
    private final User otherBuyer = Fixture.createUser(2L, "buyer-2");
    private final Item item = Fixture.createItem(1L, "item-1", "테스트 상품", 10000L);
    private final ChatRoom chatRoom = Fixture.createChatRoom(1L, buyer, item);

    @Test
    @DisplayName("ユーザー未存在")
    void notFoundWhenUserIsNull() {
        // when
        UserTokenValidator.Check result = UserTokenValidator.checkUserExists(null, messages);

        // then
        assertThat(result).isInstanceOfSatisfying(UserTokenValidator.Check.Fail.class,
                fail -> assertThat(fail.response()).isInstanceOf(IssueUserTokenResponse.UserNotFound.class));
    }

    @Test
    @DisplayName("ユーザー存在確認")
    void passesWhenUserExists() {
        // when
        UserTokenValidator.Check result = UserTokenValidator.checkUserExists(buyer, messages);

        // then
        assertThat(result).isInstanceOf(UserTokenValidator.Check.Pass.class);
    }

    @Test
    @DisplayName("チャットルーム未存在")
    void notFoundWhenChatRoomIsNull() {
        // when
        UserTokenValidator.Check result = UserTokenValidator.checkChatRoomExists(null, messages);

        // then
        assertThat(result).isInstanceOfSatisfying(UserTokenValidator.Check.Fail.class,
                fail -> assertThat(fail.response()).isInstanceOf(IssueUserTokenResponse.ChatRoomNotFound.class));
    }

    @Test
    @DisplayName("チャットルーム存在確認")
    void passesWhenChatRoomExists() {
        // when
        UserTokenValidator.Check result = UserTokenValidator.checkChatRoomExists(chatRoom, messages);

        // then
        assertThat(result).isInstanceOf(UserTokenValidator.Check.Pass.class);
    }

    @Test
    @DisplayName("所有者不一致")
    void deniesWhenOwnerMismatches() {
        // when
        UserTokenValidator.Check result = UserTokenValidator.checkOwnership(chatRoom, otherBuyer, messages);

        // then
        assertThat(result).isInstanceOfSatisfying(UserTokenValidator.Check.Fail.class,
                fail -> assertThat(fail.response()).isInstanceOf(IssueUserTokenResponse.ChatRoomAccessDenied.class));
    }

    @Test
    @DisplayName("所有者一致確認")
    void passesWhenOwnerMatches() {
        // when
        UserTokenValidator.Check result = UserTokenValidator.checkOwnership(chatRoom, buyer, messages);

        // then
        assertThat(result).isInstanceOf(UserTokenValidator.Check.Pass.class);
    }
}
