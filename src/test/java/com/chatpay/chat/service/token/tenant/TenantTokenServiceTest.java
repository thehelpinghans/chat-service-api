package com.chatpay.chat.service.token.tenant;

import com.chatpay.Fixture;
import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.dto.token.IssueTenantTokenResponse;
import com.chatpay.chat.service.room.ChatRoomService;
import com.chatpay.common.config.JwtProvider;
import com.chatpay.common.domain.Item;
import com.chatpay.common.domain.User;
import com.chatpay.common.message.MessageResolver;
import com.chatpay.common.multitenancy.TenantIdentifierResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TenantTokenServiceTest {

    @Mock
    private ChatRoomService chatRoomService;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private TenantIdentifierResolver tenantIdentifierResolver;
    @Mock
    private MessageResolver messages;

    private TenantTokenService tenantTokenService;

    private final User buyer = Fixture.createUser(1L, "buyer-1");
    private final Item item = Fixture.createItem(1L, "item-1", "테스트 상품", 10000L);
    private final ChatRoom chatRoom = Fixture.createChatRoom(1L, buyer, item);

    private TenantTokenService newTenantTokenService() {
        return new TenantTokenService(chatRoomService, jwtProvider, tenantIdentifierResolver, messages);
    }

    @Test
    @DisplayName("チャットルーム未存在")
    void chatRoomNotFoundOnIssueTenantToken() {
        // given
        given(chatRoomService.findChatRoomById(chatRoom.getId())).willReturn(Optional.empty());
        tenantTokenService = newTenantTokenService();

        // when
        IssueTenantTokenResponse response = tenantTokenService.issueTenantToken(chatRoom.getId());

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
        tenantTokenService = newTenantTokenService();

        // when
        IssueTenantTokenResponse response = tenantTokenService.issueTenantToken(chatRoom.getId());

        // then
        assertThat(response).isInstanceOfSatisfying(IssueTenantTokenResponse.Issued.class,
                issued -> assertThat(issued.response().token()).isEqualTo("jwt-token"));
    }
}
