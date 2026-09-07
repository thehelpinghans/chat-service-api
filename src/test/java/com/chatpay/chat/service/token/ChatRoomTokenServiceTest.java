package com.chatpay.chat.service.token;

import com.chatpay.Fixture;
import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.dto.token.IssueTenantTokenResponse;
import com.chatpay.chat.dto.token.IssueUserTokenResponse;
import com.chatpay.chat.service.room.ChatRoomService;
import com.chatpay.common.config.JwtProvider;
import com.chatpay.common.domain.Item;
import com.chatpay.common.domain.User;
import com.chatpay.common.message.MessageResolver;
import com.chatpay.common.multitenancy.TenantIdentifierResolver;
import com.chatpay.common.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ChatRoomTokenServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private ChatRoomService chatRoomService;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private TenantIdentifierResolver tenantIdentifierResolver;
    @Mock
    private MessageResolver messages;

    private ChatRoomTokenService chatRoomTokenService;

    private final User buyer = Fixture.createUser(1L, "buyer-1");
    private final Item item = Fixture.createItem(1L, "item-1", "테스트 상품", 10000L);
    private final ChatRoom chatRoom = Fixture.createChatRoom(1L, buyer, item);

    private ChatRoomTokenService newChatRoomTokenService() {
        return new ChatRoomTokenService(userService, chatRoomService, jwtProvider, tenantIdentifierResolver, messages);
    }

    @Test
    @DisplayName("ユーザー未存在")
    void userNotFoundOnIssueUserToken() {
        // given
        given(messages.get(anyString())).willReturn("user not found");
        given(userService.findUserByExternalId("buyer-1")).willReturn(Optional.empty());
        chatRoomTokenService = newChatRoomTokenService();

        // when
        IssueUserTokenResponse response = chatRoomTokenService.issueUserToken(chatRoom.getId(), "buyer-1");

        // then
        assertThat(response).isInstanceOf(IssueUserTokenResponse.UserNotFound.class);
        verifyNoInteractions(chatRoomService, jwtProvider);
    }

    @Test
    @DisplayName("チャットルーム未存在")
    void chatRoomNotFoundOnIssueUserToken() {
        // given
        given(messages.get(anyString())).willReturn("chatroom not found");
        given(userService.findUserByExternalId("buyer-1")).willReturn(Optional.of(buyer));
        given(chatRoomService.findChatRoomById(chatRoom.getId())).willReturn(Optional.empty());
        chatRoomTokenService = newChatRoomTokenService();

        // when
        IssueUserTokenResponse response = chatRoomTokenService.issueUserToken(chatRoom.getId(), "buyer-1");

        // then
        assertThat(response).isInstanceOf(IssueUserTokenResponse.ChatRoomNotFound.class);
        verifyNoInteractions(jwtProvider);
    }

    @Test
    @DisplayName("所有者不一致")
    void deniesIssueUserTokenWhenNotOwner() {
        // given
        given(messages.get(anyString())).willReturn("access denied");
        User otherBuyer = Fixture.createUser(2L, "buyer-2");
        given(userService.findUserByExternalId("buyer-2")).willReturn(Optional.of(otherBuyer));
        given(chatRoomService.findChatRoomById(chatRoom.getId())).willReturn(Optional.of(chatRoom));
        chatRoomTokenService = newChatRoomTokenService();

        // when
        IssueUserTokenResponse response = chatRoomTokenService.issueUserToken(chatRoom.getId(), "buyer-2");

        // then
        assertThat(response).isInstanceOf(IssueUserTokenResponse.ChatRoomAccessDenied.class);
        verifyNoInteractions(jwtProvider);
    }

    @Test
    @DisplayName("購入者トークン発行成功")
    void issuesUserTokenSuccessfully() {
        // given
        given(userService.findUserByExternalId("buyer-1")).willReturn(Optional.of(buyer));
        given(chatRoomService.findChatRoomById(chatRoom.getId())).willReturn(Optional.of(chatRoom));
        given(tenantIdentifierResolver.resolveCurrentTenantIdentifier()).willReturn(9L);
        given(jwtProvider.createToken(chatRoom.getId(), buyer.getId(), 9L)).willReturn("jwt-token");
        chatRoomTokenService = newChatRoomTokenService();

        // when
        IssueUserTokenResponse response = chatRoomTokenService.issueUserToken(chatRoom.getId(), "buyer-1");

        // then
        assertThat(response).isInstanceOfSatisfying(IssueUserTokenResponse.Issued.class,
                issued -> assertThat(issued.response().token()).isEqualTo("jwt-token"));
    }

    @Test
    @DisplayName("チャットルーム未存在(テナントトークン)")
    void chatRoomNotFoundOnIssueTenantToken() {
        // given
        given(messages.get(anyString())).willReturn("chatroom not found");
        given(chatRoomService.findChatRoomById(chatRoom.getId())).willReturn(Optional.empty());
        chatRoomTokenService = newChatRoomTokenService();

        // when
        IssueTenantTokenResponse response = chatRoomTokenService.issueTenantToken(chatRoom.getId());

        // then
        assertThat(response).isInstanceOf(IssueTenantTokenResponse.ChatRoomNotFound.class);
        verifyNoInteractions(jwtProvider);
    }

    @Test
    @DisplayName("テナントトークン発行成功(userId未指定)")
    void issuesTenantTokenWithoutUserId() {
        // given
        given(chatRoomService.findChatRoomById(chatRoom.getId())).willReturn(Optional.of(chatRoom));
        given(tenantIdentifierResolver.resolveCurrentTenantIdentifier()).willReturn(9L);
        given(jwtProvider.createToken(chatRoom.getId(), null, 9L)).willReturn("jwt-token");
        chatRoomTokenService = newChatRoomTokenService();

        // when
        IssueTenantTokenResponse response = chatRoomTokenService.issueTenantToken(chatRoom.getId());

        // then
        assertThat(response).isInstanceOfSatisfying(IssueTenantTokenResponse.Issued.class,
                issued -> assertThat(issued.response().token()).isEqualTo("jwt-token"));
    }
}
