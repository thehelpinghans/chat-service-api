package com.chatpay.common.config;

import com.chatpay.common.domain.Tenant;
import com.chatpay.common.domain.TenantStatus;
import com.chatpay.common.repository.TenantRepository;
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
 *
 * 경로별 허용 인증방식 강제: URL 네임스페이스 자체를 트러스트 레벨로 분리했다
 * (/api/v1/user/** = Bearer 전용, 그 외 /api/v1/** = X-Api-Key 전용). BEARER_PATH_PREFIX
 * 접두사 하나로 authenticateBearer/authenticateApiKey를 분기하므로, 예전처럼 특정 엔드포인트
 * 모양(예: 숫자 chatRoomId, /tenant 접미사)을 정규식으로 하나하나 맞출 필요가 없고, 새 엔드포인트를
 * 추가해도 올바른 네임스페이스 밑에만 두면 이 필터를 전혀 안 고쳐도 된다. 각 메서드가 반대쪽
 * 헤더를 들여다보는 부분은 인증 판단이 아니라 에러 메시지를 정확히 주기 위한 부가 로직이다.
 */
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

    // 구매자 브라우저(우리 SDK/iframe) 전용 — 한 번의 파싱·서명 검증으로 tenantId·userId를 함께 추출(DB 조회 없음)
    private void authenticateBearer(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String authHeader = request.getHeader("Authorization"); // 이 메서드 호출 시(=Bearer 전용 경로) 항상 실행

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            boolean sentApiKeyInstead = request.getHeader(API_KEY_HEADER) != null;
            if (sentApiKeyInstead) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "이 API는 Bearer 토큰이 필요합니다. X-Api-Key로는 접근할 수 없습니다.");
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "인증 정보가 없습니다. Bearer 토큰을 포함해주세요.");
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
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "유효하지 않은 토큰입니다.");
            return;
        }

        chain.doFilter(request, response);
    }

    // 테넌트 서버(서버-투-서버) 전용 — DB 조회로 tenantId만 검증
    private void authenticateApiKey(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String apiKey = request.getHeader(API_KEY_HEADER); // 이 메서드 호출 시(=X-Api-Key 전용 경로) 항상 실행

        if (apiKey == null || apiKey.isBlank()) {
            // X-Api-Key 헤더가 없거나 빈 값일 때만 true
            String authHeader = request.getHeader("Authorization");
            boolean sentBearerInstead = authHeader != null && authHeader.startsWith("Bearer ");
            if (sentBearerInstead) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "이 API는 X-Api-Key가 필요합니다. Bearer 토큰으로는 접근할 수 없습니다.");
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "인증 정보가 없습니다. X-Api-Key 헤더를 포함해주세요.");
            }
            return;
        }

        Optional<Tenant> tenantOpt = tenantRepository.findByApiKey(apiKey);

        if (tenantOpt.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "유효하지 않은 API 키입니다.");
            return;
        }

        Tenant tenant = tenantOpt.get();

        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "비활성화된 테넌트입니다.");
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
