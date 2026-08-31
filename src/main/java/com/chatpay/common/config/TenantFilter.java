package com.chatpay.common.config;

import com.chatpay.common.domain.Tenant;
import com.chatpay.common.domain.TenantStatus;
import com.chatpay.common.repository.TenantRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
@Order(1)
public class TenantFilter implements Filter {

    private static final String API_KEY_HEADER = "X-Api-Key";

    // 구매자 브라우저(우리 SDK/iframe)만 호출하는 Bearer 전용 네임스페이스. 그 외 /api/v1/** 는 X-Api-Key 전용.
    private static final String BEARER_PATH_PREFIX = "/api/v1/user/";

    // ChatMessageController 등에서 @RequestAttribute(USER_ATTRIBUTE)로 userId 수령 (Bearer 경로 전용)
    public static final String USER_ATTRIBUTE = "CURRENT_USER_ID";

    // ChatMessageController 등에서 @RequestAttribute(CHAT_ROOM_ATTRIBUTE)로 토큰의 chatRoomId claim 수령
    // (BOLA 방지 — 경로변수 chatRoomId와 비교하는 데 씀, docs/embed-widget-security-design.md 참고)
    public static final String CHAT_ROOM_ATTRIBUTE = "CURRENT_CHAT_ROOM_ID";

    private final TenantRepository tenantRepository;
    private final JwtProvider jwtProvider;

    public TenantFilter(TenantRepository tenantRepository, JwtProvider jwtProvider) {
        this.tenantRepository = tenantRepository;
        this.jwtProvider = jwtProvider;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 개발용 경로 bypass: H2 Console, Swagger UI, OpenAPI 스펙, WebSocket
        if (httpRequest.getRequestURI().matches("^/(h2-console|swagger-ui.*|v3/api-docs|.*\\.html|ws.*).*")) {
            chain.doFilter(request, response);
            return;
        }

        if (httpRequest.getRequestURI().startsWith(BEARER_PATH_PREFIX)) {
            authenticateBearer(httpRequest, httpResponse, chain);
        } else {
            authenticateApiKey(httpRequest, httpResponse, chain);
        }
    }

    private void authenticateBearer(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String authHeader = request.getHeader("Authorization"); // 이 메서드 호출 시(=Bearer 전용 경로) 항상 실행

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            boolean sentApiKeyInstead = request.getHeader(API_KEY_HEADER) != null;
            if (sentApiKeyInstead) {
                log.warn("Bearer authentication rejected: X-Api-Key header used on Bearer-only endpoint, uri={}", request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            } else {
                log.warn("Bearer authentication rejected: missing Authorization header, uri={}", request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            }
            return;
        }

        try {
            Claims claims = jwtProvider.getClaims(authHeader.substring(7)); // Bearer 헤더 형식이 맞을 때만 실행 — 서명 검증 시도
            Long tenantId = claims.get("tenantId", Long.class);
            Long userId = claims.get("userId", Long.class);
            Long chatRoomId = claims.get("chatRoomId", Long.class);
            RequestAttributes attrs = RequestContextHolder.currentRequestAttributes();
            attrs.setAttribute(TenantIdentifierResolver.TENANT_ATTRIBUTE, tenantId, RequestAttributes.SCOPE_REQUEST);
            attrs.setAttribute(USER_ATTRIBUTE, userId, RequestAttributes.SCOPE_REQUEST);
            attrs.setAttribute(CHAT_ROOM_ATTRIBUTE, chatRoomId, RequestAttributes.SCOPE_REQUEST);
        } catch (JwtException e) {
            log.warn("Bearer authentication rejected: JWT validation failed - {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        chain.doFilter(request, response);
    }

    private void authenticateApiKey(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey == null || apiKey.isBlank()) {
            // X-Api-Key 헤더가 없거나 빈 값일 때만 true
            String authHeader = request.getHeader("Authorization");
            boolean sentBearerInstead = authHeader != null && authHeader.startsWith("Bearer ");
            if (sentBearerInstead) {
                log.warn("API key authentication rejected: Bearer token used on X-Api-Key-only endpoint, uri={}", request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            } else {
                log.warn("API key authentication rejected: missing X-Api-Key header, uri={}", request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            }
            return;
        }

        Optional<Tenant> tenantOpt = tenantRepository.findByApiKey(apiKey);

        if (tenantOpt.isEmpty()) {
            log.warn("API key authentication rejected: invalid API key, uri={}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        Tenant tenant = tenantOpt.get();

        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            log.warn("API key authentication rejected: tenant is not active, tenantId={}, status={}", tenant.getId(), tenant.getStatus());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        RequestContextHolder.currentRequestAttributes()
                .setAttribute(TenantIdentifierResolver.TENANT_ATTRIBUTE,
                        tenant.getId(), RequestAttributes.SCOPE_REQUEST);

        chain.doFilter(request, response);
    }
}
// TODO: 요청마다 DB 조회 발생 → Caffeine 등 로컬 캐시(TTL 5분) 적용 권장, tenant.status 변경 시 캐시 무효화 필요
// TODO: 인증 실패 상태코드(401/403)별 의미를 API 문서로 정리 필요.