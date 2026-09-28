package com.chatpay.trade.dto;

public record WebhookPayload(Long tradeId, String externalItemId, Long amount) {}
