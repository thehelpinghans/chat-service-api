package com.chatpay.saas.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // User/Item/ChatRoom 어느 단계에서 경합이 나든 동일하게 처리: 바디 없는 409, 클라이언트가 PUT을
    // 처음부터 다시 호출해야 함(재시도 시 승자의 트랜잭션이 이미 전부 커밋되어 있어 1회 재시도로 확정됨 -
    // 하나의 @Transactional 안에서 User/Item/ChatRoom이 원자적으로 커밋되므로).
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Void> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
}
