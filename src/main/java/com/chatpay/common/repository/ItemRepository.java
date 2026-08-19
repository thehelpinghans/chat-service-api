package com.chatpay.common.repository;

import com.chatpay.common.domain.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    Optional<Item> findByExternalItemId(String externalItemId);
}
