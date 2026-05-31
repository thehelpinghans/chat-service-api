package com.chatpay.saas.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Tenant {
    //2차 캐시(Hibernate 2nd Level Cache) 로 커버 가능한지? 검토, 테넌트는 수정이 거의 없으니
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TenantStatus status;

    @Column(nullable = false, length = 255)
    private String webhookUrl;

    @Column(nullable = false, unique = true, length = 64)
    private String apiKey;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Tenant(String name, TenantStatus status, String webhookUrl) {
        this.name = name;
        this.status = status;
        this.webhookUrl = webhookUrl;
    }

    @PrePersist
    public void generateApiKey() {
        if (this.apiKey == null) {
            this.apiKey = UUID.randomUUID().toString().replace("-", "");
        }
    }

    public void updateStatus(TenantStatus status) {
        this.status = status;
    }
}
