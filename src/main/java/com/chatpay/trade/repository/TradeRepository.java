package com.chatpay.trade.repository;

import com.chatpay.trade.domain.Trade;
import com.chatpay.trade.domain.TradeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TradeRepository extends JpaRepository<Trade, Long> {
    Optional<Trade> findByChatMessageId(Long chatMessageId);

    Optional<Trade> findByChatRoomIdAndTradeStatus(Long id, TradeStatus tradeStatus);

}
