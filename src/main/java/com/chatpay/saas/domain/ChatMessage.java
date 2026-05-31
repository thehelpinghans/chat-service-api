package com.chatpay.saas.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageType messageType;

    @Column(nullable = false)
    private String content;

    @OneToOne(mappedBy = "chatMessage")
    private Trade trade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chatroom_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    public static ChatMessage create(ChatRoom chatRoom, User user,
                                     String content, MessageType messageType) {
        ChatMessage msg = new ChatMessage();
        msg.chatRoom = chatRoom;
        msg.user = user;
        msg.content = content;
        msg.messageType = messageType;
        return msg;
    }
}
