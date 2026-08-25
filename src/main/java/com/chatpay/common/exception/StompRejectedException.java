package com.chatpay.common.exception;

public class StompRejectedException extends RuntimeException {
    public StompRejectedException(String clientMessage) {
        super(clientMessage);
    }
}
