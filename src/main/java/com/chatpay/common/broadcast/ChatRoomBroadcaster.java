package com.chatpay.common.broadcast;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatRoomBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcast(Long chatRoomId, Object payload) {
        messagingTemplate.convertAndSend("/topic/chat/" + chatRoomId, payload);
    }
}
