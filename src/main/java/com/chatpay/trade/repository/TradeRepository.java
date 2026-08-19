package com.chatpay.trade.repository;

import com.chatpay.trade.domain.Trade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TradeRepository extends JpaRepository<Trade, Long> {
    Optional<Trade> findByChatMessageId(Long chatMessageId);
}
