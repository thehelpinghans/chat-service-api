package com.chatpay.saas.config;

import com.chatpay.saas.domain.Tenant;
import com.chatpay.saas.domain.TenantStatus;
import com.chatpay.saas.repository.TenantRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.io.IOException;
import java.util.Optional;

/**
 * 테넌트(+ Bearer 경로의 사용자) 식별 필터. @Order(1) — 모든 필터/인터셉터보다 먼저 실행.
 *
 * 원칙적 책임은 "이 요청이 어느 테넌트 소속인지" 식별이다(X-Api-Key 경로는 실제로 이것만 함).
 * 다만 Bearer JWT 경로에서는 tenantId와 userId가 같은 토큰의 같은 파싱 결과에서 나오므로,
 * 별도 필터(JwtUserFilter)로 나눠 JWT를 한 번 더 파싱·검증하면 (1)서명 검증 중복 연산과
 * (2)"JwtUserFilter가 항상 이 필터 뒤에 실행된다"는 @Order 값에 의존하는 숨은 결합이 생긴다.
 * 이 코드베이스는 SRP를 일부 양보하고 그 비용·결합을 없애는 쪽을 택했다 — Bearer 경로에서는
 * 이 필터가 한 번의 파싱으로 tenantId·userId를 모두 추출해 세팅한다.
 *
 * (예전엔 JwtAuthInterceptor가 동일하게 둘 다 추출했지만, Spring 공식 문서가 보안 목적엔
 *  HandlerInterceptor보다 Servlet Filter 체인 통합을 권장하여 Filter로 전환한 바 있음.)
 *
 * 연계 흐름:
 *   테넌트 서버가 X-Api-Key 헤더를 포함해 요청
 *     → 이 필터 (apiKey → TenantRepository 조회 → tenantId를 RequestAttributes에 세팅)
 *       → TenantIdentifierResolver (Hibernate 쿼리 시 TENANT_ATTRIBUTE 읽어 WHERE tenant_id = ? 자동 적용)
 *
 * 헤더 타입으로 분기한다:
 *   Bearer 헤더 → JWT claim에서 tenantId·userId를 함께 파싱 (DB 조회 없음)
 *   X-Api-Key   → 이 필터에서 DB 조회로 tenantId만 검증
 *   둘 다 없음  → 401 반환
 */
@Component
@Order(1)
public class TenantFilter implements Filter {

    private static final String API_KEY_HEADER = "X-Api-Key";

    // ChatMessageController 등에서 @RequestAttribute(USER_ATTRIBUTE)로 userId 수령 (Bearer 경로 전용)
    public static final String USER_ATTRIBUTE = "CURRENT_USER_ID";

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
        if (httpRequest.getRequestURI().matches("^/(h2-console|swagger-ui.*|v3/api-docs|test-chat\\.html|ws.*).*")) {
            chain.doFilter(request, response);
            return;
        }

        // Bearer 토큰이 있으면 JWT 인증 경로(구매자 브라우저)
        // 한 번의 파싱·서명 검증으로 tenantId·userId를 모두 추출 — 별도 필터로 나눠
        // 같은 토큰을 또 검증하면 중복 연산 + 필터 간 순서 의존이 생기므로 여기서 함께 처리
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                Claims claims = jwtProvider.getClaims(authHeader.substring(7));
                Long tenantId = claims.get("tenantId", Long.class);
                Long userId = claims.get("userId", Long.class);
                RequestAttributes attrs = RequestContextHolder.currentRequestAttributes();
                attrs.setAttribute(TenantIdentifierResolver.TENANT_ATTRIBUTE, tenantId, RequestAttributes.SCOPE_REQUEST);
                attrs.setAttribute(USER_ATTRIBUTE, userId, RequestAttributes.SCOPE_REQUEST);
            } catch (JwtException e) {
                httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid JWT token");
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        String apiKey = httpRequest.getHeader(API_KEY_HEADER);

        if (apiKey == null || apiKey.isBlank()) {
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing X-Api-Key header");
            return;
        }

        Optional<Tenant> tenantOpt = tenantRepository.findByApiKey(apiKey);

        if (tenantOpt.isEmpty()) {
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
            return;
        }

        Tenant tenant = tenantOpt.get();

        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Tenant is not active");
            return;
        }

        // tenantId를 RequestAttributes(SCOPE_REQUEST)에 저장
        // → TenantIdentifierResolver.resolveCurrentTenantIdentifier()가 이 값을 읽어 Hibernate 필터에 적용
        // SCOPE_REQUEST는 요청 완료 시 Spring이 자동 소멸 → 수동 clear() 불필요
        RequestContextHolder.currentRequestAttributes()
                .setAttribute(TenantIdentifierResolver.TENANT_ATTRIBUTE,
                        tenant.getId(), RequestAttributes.SCOPE_REQUEST);

        chain.doFilter(request, response);
    }
}
// TODO: 요청마다 DB 조회 발생 → Caffeine 등 로컬 캐시(TTL 5분) 적용 권장
//   tenant.status 변경 시 캐시 무효화 필요
