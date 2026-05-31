package com.chatpay.saas.config;

import com.chatpay.saas.domain.Tenant;
import com.chatpay.saas.domain.TenantStatus;
import com.chatpay.saas.repository.TenantRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.io.IOException;
import java.util.Optional;

@Component
@Order(1)
public class TenantFilter implements Filter {

    private static final String API_KEY_HEADER = "X-Api-Key";

    private final TenantRepository tenantRepository;

    public TenantFilter(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 테넌트 인증 제외 경로
        // WebSocket(/ws): STOMP CONNECT 프레임의 JWT로 인증 (ChannelInterceptor 담당)
        // 개발용 경로: H2 Console, Swagger UI, OpenAPI 스펙, 정적 리소스
        if (httpRequest.getRequestURI().matches("^/(h2-console|swagger-ui.*|v3/api-docs|test-chat\\.html|ws.*).*")) {
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

        RequestContextHolder.currentRequestAttributes()
                .setAttribute(TenantIdentifierResolver.TENANT_ATTRIBUTE,
                        tenant.getId(), RequestAttributes.SCOPE_REQUEST);

        chain.doFilter(request, response);
    }
}
// TODO: 요청마다 DB 조회 발생 → Caffeine 등 로컬 캐시(TTL 5분) 적용 권장
//   tenant.status 변경 시 캐시 무효화 필요