package com.chatpay.common.exception;

import org.springframework.messaging.MessagingException;

public class StompRejectedException extends MessagingException {
    public StompRejectedException(String clientMessage) {
        super(clientMessage);
    }
}
