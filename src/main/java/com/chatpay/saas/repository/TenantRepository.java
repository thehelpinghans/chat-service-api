package com.chatpay.saas.repository;

import com.chatpay.saas.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByApiKey(String apiKey);
}
