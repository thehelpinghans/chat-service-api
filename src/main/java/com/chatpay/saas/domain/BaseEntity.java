package com.chatpay.saas.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.TenantId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@MappedSuperclass // 중요: 이 클래스는 테이블로 생성되지 않고, 자식 클래스에게 매핑 정보만 전달합니다.
@EntityListeners(AuditingEntityListener.class) // Auditing 기능 활성화
public class BaseEntity {

    @TenantId
    @Column(nullable = false)
    private Long tenantId;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
