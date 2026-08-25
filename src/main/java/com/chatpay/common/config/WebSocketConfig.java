package com.chatpay.common.config;

import com.chatpay.common.exception.StompRejectedException;
import com.chatpay.common.message.MessageResolver;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Map;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String CHAT_TOPIC_PREFIX = "/topic/chat/";
    private final JwtProvider jwtProvider;
    private final MessageResolver messages;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*"); // 개발 환경용, 운영 시 도메인 지정 필요;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[] {10000, 10000})
                .setTaskScheduler(heartbeatTaskScheduler());
    }

    @Bean
    public TaskScheduler heartbeatTaskScheduler() {
        return new ThreadPoolTaskScheduler();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {

            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {

                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor == null) return message;

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");

                    if (authHeader == null) {
                        log.warn("STOMP CONNECT rejected: missing Authorization header");
                        throw new StompRejectedException(messages.get("stomp.connect.unauthorized"));
                    }
                    if (!authHeader.startsWith("Bearer ")) {
                        log.warn("STOMP CONNECT rejected: malformed Authorization header");
                        throw new StompRejectedException(messages.get("stomp.connect.unauthorized"));
                    }

                    Map<String, Object> sessionAttributes = accessor.getSessionAttributes();

                    if (sessionAttributes == null) {
                        log.error("STOMP CONNECT rejected: session attributes unavailable - check WebSocket configuration");
                        throw new StompRejectedException(messages.get("stomp.connect.session-error"));
                    }

                    try {
                        Claims claims = jwtProvider.getClaims(authHeader.substring(7));
                        sessionAttributes.put("chatRoomId", claims.get("chatRoomId", Long.class));

                    } catch (JwtException | IllegalArgumentException e) {
                        log.warn("STOMP CONNECT rejected: JWT validation failed - {}", e.getMessage());
                        throw new StompRejectedException(messages.get("stomp.connect.unauthorized"));
                    }

                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();

                    if (destination == null || !destination.startsWith(CHAT_TOPIC_PREFIX)) {
                        log.warn("STOMP SUBSCRIBE rejected: invalid destination={}", destination);
                        throw new StompRejectedException(messages.get("stomp.subscribe.invalid-request"));
                    }

                    Map<String, Object> sessionAttributes = accessor.getSessionAttributes();

                    Long tokenChatRoomId = sessionAttributes == null ? null : (Long) sessionAttributes.get("chatRoomId");

                    Long destinationChatRoomId;
                    try {
                        destinationChatRoomId = Long.valueOf(destination.substring(CHAT_TOPIC_PREFIX.length()));

                    } catch (NumberFormatException e) {
                        log.warn("STOMP SUBSCRIBE rejected: failed to parse chatRoomId, destination={}", destination);
                        throw new StompRejectedException(messages.get("stomp.subscribe.invalid-request"));
                    }

                    if (!destinationChatRoomId.equals(tokenChatRoomId)) {
                        log.warn("STOMP SUBSCRIBE rejected: chatRoomId mismatch (destination={}, token={})", destinationChatRoomId, tokenChatRoomId);
                        throw new StompRejectedException(messages.get("stomp.subscribe.access-denied"));
                    }
                }
                return message;
            }
        });
    }
}
