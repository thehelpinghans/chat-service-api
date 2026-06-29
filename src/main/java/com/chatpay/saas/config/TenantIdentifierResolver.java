package com.chatpay.saas.config;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Map;

@Component
public class TenantIdentifierResolver
        implements CurrentTenantIdentifierResolver<Long>, HibernatePropertiesCustomizer {

    static final String TENANT_ATTRIBUTE = "CURRENT_TENANT_ID";

    @Override
    public Long resolveCurrentTenantIdentifier() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        //앱 시작, 테넌트 없는 시스템 컨텍스트
        if (requestAttributes == null) {
            return 0L;
        }
        //실제 HTTP 요청
        Long tenantId = (Long) requestAttributes.getAttribute(TENANT_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        // null이면 0L 반환: TenantFilter가 findByApiKey로 테넌트를 조회하는 시점에
        // 아직 tenantId가 세팅되기 전이므로 Session 오픈만 허용 (Tenant는 @TenantId 없어 필터 미적용)
        if (tenantId == null) {
            return 0L;
        }
        return tenantId;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }

}
