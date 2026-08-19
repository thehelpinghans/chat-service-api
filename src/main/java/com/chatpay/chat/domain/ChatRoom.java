package com.chatpay.chat.domain;

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
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "item_id", "tenant_id"})
})
public class ChatRoom extends BaseEntity {

    //TODO createTrade 중복PENDING 방지: ChatRoom을 Aggregate Root로 @Version 도입 검토 중.
    // OPTIMISTIC_FORCE_INCREMENT 재현 테스트로 신뢰성 확인 후 필드 추가 여부 결정.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @OneToMany(mappedBy = "chatRoom")
    private List<ChatMessage> chatMessages = new ArrayList<>();

    public static ChatRoom create(User user, Item item) {
        ChatRoom chatRoom = new ChatRoom();
        chatRoom.user = user;
        chatRoom.item = item;
        return chatRoom;
    }
}
