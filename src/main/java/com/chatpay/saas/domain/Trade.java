package com.chatpay.saas.domain;

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

    // @MapsId 불가: Trade.id != ChatMessage.id (독립 PK). TEXT 메시지는 Trade가 없으므로 PK 공유 불가
    // 생성 순서: ChatMessage(PAYMENT_REQUEST) 먼저 INSERT → Trade INSERT 시 chat_message_id 확정
    // chat_message_id NOT NULL이므로 순서 역전 시 DB 제약 위반
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

    public static Trade create(User user, Item item, String itemName, Long amount, ChatMessage chatMessage) {
        Trade trade = new Trade();
        trade.user = user;
        trade.item = item;
        trade.itemName = itemName;
        trade.amount = amount;
        trade.tradeStatus = TradeStatus.PENDING;
        trade.chatMessage = chatMessage;
        return trade;
    }
}
