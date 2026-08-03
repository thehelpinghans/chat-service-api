package com.chatpay.saas.dto.trade;

import com.chatpay.saas.domain.TradeStatus;

public record TradeResponse(
        Long chatMessageId,
        TradeStatus tradeStatus
) {}