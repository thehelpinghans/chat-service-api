package com.chatpay.trade.service.creation;

import com.chatpay.Fixture;
import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.common.domain.Item;
import com.chatpay.common.domain.User;
import com.chatpay.common.message.MessageResolver;
import com.chatpay.trade.dto.TradeCreateResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TradeCreateValidatorTest {

    @Mock
    private MessageResolver messages;

    private final User buyer = Fixture.createUser(1L, "buyer-1");
    private final Item item = Fixture.createItem(1L, "item-1", "테스트 상품", 10000L);
    private final ChatRoom chatRoom = Fixture.createChatRoom(1L, buyer, item);

    @Test
    @DisplayName("購入者によるリクエスト")
    void passesWhenUserIdIsNull() {
        // when
        TradeCreateValidator.Check result = TradeCreateValidator.checkSellerOnly(null, messages);

        // then
        assertThat(result).isInstanceOf(TradeCreateValidator.Check.Pass.class);
    }

    @Test
    @DisplayName("オペレーターによるリクエスト")
    void deniesWhenUserIdIsPresent() {
        // when
        TradeCreateValidator.Check result = TradeCreateValidator.checkSellerOnly(999L, messages);

        // then
        assertThat(result).isInstanceOfSatisfying(TradeCreateValidator.Check.Fail.class,
                fail -> assertThat(fail.response()).isInstanceOf(TradeCreateResponse.SellerOnly.class));
    }

    @Test
    @DisplayName("chatRoomId一致確認")
    void passesWhenChatRoomIdMatches() {
        // when
        TradeCreateValidator.Check result = TradeCreateValidator.checkChatRoomAccess(1L, 1L, messages);

        // then
        assertThat(result).isInstanceOf(TradeCreateValidator.Check.Pass.class);
    }

    @Test
    @DisplayName("chatRoomId不一致")
    void deniesWhenChatRoomIdMismatches() {
        // when
        TradeCreateValidator.Check result = TradeCreateValidator.checkChatRoomAccess(1L, 2L, messages);

        // then
        assertThat(result).isInstanceOfSatisfying(TradeCreateValidator.Check.Fail.class,
                fail -> assertThat(fail.response()).isInstanceOf(TradeCreateResponse.ChatRoomAccessDenied.class));
    }

    @Test
    @DisplayName("トークンにchatRoomId未指定")
    void deniesWhenTokenChatRoomIdIsNull() {
        // when
        TradeCreateValidator.Check result = TradeCreateValidator.checkChatRoomAccess(1L, null, messages);

        // then
        assertThat(result).isInstanceOfSatisfying(TradeCreateValidator.Check.Fail.class,
                fail -> assertThat(fail.response()).isInstanceOf(TradeCreateResponse.ChatRoomAccessDenied.class));
    }

    @Test
    @DisplayName("チャットルーム未存在")
    void notFoundWhenChatRoomIsNull() {
        // when
        TradeCreateValidator.Check result = TradeCreateValidator.checkChatRoomExists(null, messages);

        // then
        assertThat(result).isInstanceOfSatisfying(TradeCreateValidator.Check.Fail.class,
                fail -> assertThat(fail.response()).isInstanceOf(TradeCreateResponse.ChatRoomNotFound.class));
    }

    @Test
    @DisplayName("チャットルーム存在確認")
    void passesWhenChatRoomExists() {
        // when
        TradeCreateValidator.Check result = TradeCreateValidator.checkChatRoomExists(chatRoom, messages);

        // then
        assertThat(result).isInstanceOf(TradeCreateValidator.Check.Pass.class);
    }
}
