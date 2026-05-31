package com.chatpay.saas.config;

import io.jsonwebtoken.Claims;
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

    // JwtProvider를 주입받아 CONNECT 시 토큰 검증에 사용
    // TODO: 생성자 주입으로 JwtProvider 추가 필요
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
        // /app 으로 시작하는 메시지 → @MessageMapping 핸들러로 라우팅
        // 예: 클라이언트가 /app/chat/1 로 전송 → @MessageMapping("/chat/{chatRoomId}") 진입
        config.setApplicationDestinationPrefixes("/app");

        // /topic 으로 시작하는 경로를 구독한 클라이언트에게 메시지 브로드캐스트
        // 예: 클라이언트가 /topic/chat/1 구독 → 해당 채팅방 메시지 수신
        config.enableSimpleBroker("/topic");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // 1. STOMP 헤더에서 토큰 추출
                    String token = accessor.getFirstNativeHeader("Authorization");
                    // 2. 토큰 유효성 검증
                    // 3. 토큰에서 tenantId 추출 후 WebSocket 세션에 저장
                    try {
                        Claims claims = jwtProvider.getClaims(token); //실패 시 연결 거부 처리
                        accessor.getSessionAttributes().put("tenantId", claims.get("tenantId", Long.class));
                        accessor.getSessionAttributes().put("userId", claims.get("userId", Long.class));
                    } catch (JwtException | IllegalArgumentException e) {
                        throw new MessageDeliveryException(e.getMessage());
                        //throw new MessageDeliveryException("유효하지 않은 토큰"); 운영시 처리
                    }
                }
                return message;
            }
        });
    }
}
