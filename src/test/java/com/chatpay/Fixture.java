package com.chatpay;

import com.chatpay.chat.domain.ChatMessage;
import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.domain.MessageType;
import com.chatpay.common.domain.Item;
import com.chatpay.common.domain.Tenant;
import com.chatpay.common.domain.TenantStatus;
import com.chatpay.common.domain.User;
import com.chatpay.trade.domain.Trade;
import com.chatpay.trade.domain.TradeStatus;
import com.chatpay.trade.domain.Wallet;
import org.springframework.test.util.ReflectionTestUtils;

// 빌더로 못 채우는 필드(@Id @GeneratedValue, @MapsId 등)는 ReflectionTestUtils로 직접 주입.
// 단위테스트 전용 — Mockito 목(리포지토리/서비스)이 리턴할 순수 도메인 객체를 만드는 용도라
// 영속성 컨텍스트를 거치지 않고, DB에 실제로 저장되지 않는다.
public class Fixture {

    public static User createUser(Long id, String externalId) {
        User user = User.create(externalId);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    public static Item createItem(Long id, String externalItemId, String name, Long price) {
        Item item = Item.create(externalItemId, name, price);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    public static ChatRoom createChatRoom(Long id, User user, Item item) {
        ChatRoom chatRoom = ChatRoom.create(user, item);
        ReflectionTestUtils.setField(chatRoom, "id", id);
        return chatRoom;
    }

    public static ChatMessage createChatMessage(Long id, ChatRoom chatRoom, User user, String content, MessageType messageType) {
        ChatMessage chatMessage = ChatMessage.create(chatRoom, user, content, messageType);
        ReflectionTestUtils.setField(chatMessage, "id", id);
        return chatMessage;
    }

    public static Trade createTrade(Long id, User user, Item item, String itemName, Long amount,
                                     ChatRoom chatRoom, ChatMessage chatMessage) {
        Trade trade = Trade.create(user, item, itemName, amount, chatRoom, chatMessage);
        ReflectionTestUtils.setField(trade, "id", id);
        return trade;
    }

    public static Trade createTradeWithStatus(Long id, User user, Item item, String itemName, Long amount,
                                               ChatRoom chatRoom, ChatMessage chatMessage, TradeStatus status) {
        Trade trade = createTrade(id, user, item, itemName, amount, chatRoom, chatMessage);
        ReflectionTestUtils.setField(trade, "tradeStatus", status);
        return trade;
    }

    public static Wallet createWallet(User user, Long balance) {
        Wallet wallet = Wallet.create(user);
        ReflectionTestUtils.setField(wallet, "id", user.getId());
        ReflectionTestUtils.setField(wallet, "balance", balance);
        return wallet;
    }

    public static Tenant createTenant(Long id, String name, TenantStatus status, String webhookUrl) {
        Tenant tenant = Tenant.builder().name(name).status(status).webhookUrl(webhookUrl).build();
        ReflectionTestUtils.setField(tenant, "id", id);
        return tenant;
    }
}
