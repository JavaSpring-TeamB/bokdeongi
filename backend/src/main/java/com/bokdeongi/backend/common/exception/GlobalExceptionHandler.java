package com.bokdeongi.backend.common.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/*
애플리케이션 전역에서 발생하는 예외를 처리하는 클래스
예외 유형에 따라 적절한 HTTP 상태 코드와 오류 메시지 반환
 */

@RestControllerAdvice
public class GlobalExceptionHandler {

    /* 잘못된 요청 값으로 발생한 예외를 HTTP 400으로 처리 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(
            IllegalArgumentException e
    ) {
        return ResponseEntity
                .badRequest()
                .body(Map.of("message", e.getMessage()));
    }
}