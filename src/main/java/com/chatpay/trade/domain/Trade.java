package com.chatpay.trade.domain;

import com.chatpay.chat.domain.ChatMessage;
import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.common.domain.BaseEntity;
import com.chatpay.common.domain.Item;
import com.chatpay.common.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trade extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String itemName;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TradeStatus tradeStatus;

    @Version
    private Long version;

    @Column(name = "pending_dedup_key", insertable = false, updatable = false, unique = true,
            columnDefinition = "BIGINT GENERATED ALWAYS AS " +
                    "(CASE WHEN trade_status = 'PENDING' THEN chat_room_id ELSE NULL END)")
    private Long pendingDedupKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_message_id", nullable = false)
    private ChatMessage chatMessage;

    @OneToMany(mappedBy = "trade")
    private List<WalletTransaction> walletTransactions = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    public static Trade create(User user, Item item, String itemName, Long amount, ChatRoom chatRoom, ChatMessage chatMessage) {
        Trade trade = new Trade();
        trade.user = user;
        trade.item = item;
        trade.itemName = itemName;
        trade.amount = amount;
        trade.tradeStatus = TradeStatus.PENDING;
        trade.chatRoom = chatRoom;
        trade.chatMessage = chatMessage;
        return trade;
    }

    public void changeStatus(TradeStatus tradeStatus) {
        if (this.tradeStatus != TradeStatus.PENDING) {
            throw new IllegalStateException("Trade must be PENDING to change status, but was: " + this.tradeStatus);
        }
        this.tradeStatus = tradeStatus;
    }
}
