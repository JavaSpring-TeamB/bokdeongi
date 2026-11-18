package com.bokdeongi.backend.benefit.ocr;

import com.bokdeongi.backend.common.exception.ApiErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 서류 검토 에러 → 공통 에러 응답 (common/exception/GlobalExceptionHandler는 건드리지 않으려고 따로 둔다) */
@RestControllerAdvice
public class DocumentCheckExceptionHandler {

    @ExceptionHandler(DocumentCheckException.class)
    public ResponseEntity<ApiErrorResponse> handle(DocumentCheckException e) {
        return ResponseEntity.status(e.getStatus()).body(ApiErrorResponse.of(e.getCode(), e.getMessage()));
    }
}
