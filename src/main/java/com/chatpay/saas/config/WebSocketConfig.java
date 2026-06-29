package com.chatpay.saas.config;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtProvider jwtProvider;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 클라이언트가 WebSocket 연결을 맺는 HTTP 엔드포인트
        // React: new Client({ brokerURL: 'ws://localhost:8080/ws' })
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*"); // 개발 환경용, 운영 시 도메인 지정 필요;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // /topic 으로 시작하는 경로를 구독한 클라이언트에게 메시지 브로드캐스트
        // 예: 클라이언트가 /topic/chat/1 구독 → 해당 채팅방 메시지 수신
        config.enableSimpleBroker("/topic");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // STOMP CONNECT 시 JWT 서명 검증 — 미인증 클라이언트의 /topic 구독 차단
        // WebSocket은 서버→클라이언트 push 전용이므로 세션에 tenantId/userId 저장 불필요
        // 메시지 저장(INSERT)은 HTTP POST에서 처리하며, JWT 파싱은 해당 요청에서 별도 수행
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        throw new MessageDeliveryException("Missing or malformed Authorization header");
                    }
                    try {
                        jwtProvider.getClaims(authHeader.substring(7));
                    } catch (JwtException | IllegalArgumentException e) {
                        throw new MessageDeliveryException(e.getMessage());
                    }
                }
                return message;
            }
        });
    }
}
