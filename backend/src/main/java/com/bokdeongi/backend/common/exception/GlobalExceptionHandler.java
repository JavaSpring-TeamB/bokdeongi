package com.bokdeongi.backend.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * 애플리케이션 전역 예외 처리 클래스
 * 노션 API 명세의 에러 코드와 응답 형식을 적용한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 잘못된 요청 처리 (COMMON_001 / 400)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(
            IllegalArgumentException e
    ) {
        String message = e.getMessage() != null
                ? e.getMessage()
                : "잘못된 요청입니다.";

        return ResponseEntity
                .badRequest()
                .body(ApiErrorResponse.of("COMMON_001", message));
    }

    /**
     * 입력값 검증 실패 처리 (COMMON_002 / 400)
     * 필드별 오류 정보를 함께 반환한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException e
    ) {
        List<ApiErrorResponse.FieldErrorDetail> fieldErrors =
                e.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(error -> new ApiErrorResponse.FieldErrorDetail(
                                error.getField(),
                                error.getDefaultMessage()
                        ))
                        .toList();

        return ResponseEntity
                .badRequest()
                .body(new ApiErrorResponse(
                        "COMMON_002",
                        "입력값을 확인해주세요.",
                        fieldErrors
                ));
    }

    /**
     * 이메일 중복 처리 (AUTH_001 / 409)
     */
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateEmail(
            DuplicateEmailException e
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(
                        "AUTH_001",
                        e.getMessage()
                ));
    }

    /**
     * 로그인 인증 실패 처리 (AUTH_002 / 401)
     * 이메일 존재 여부와 비밀번호 오류를 구분하지 않는다.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(
            BadCredentialsException e
    ) {
        boolean isRefreshTokenError =
                e.getMessage() != null
                        && e.getMessage().contains("Refresh Token");

        String code = isRefreshTokenError ? "AUTH_004" : "AUTH_002";

        String message = isRefreshTokenError
                ? "유효하지 않거나 만료된 Refresh Token입니다."
                : "이메일 또는 비밀번호가 올바르지 않습니다.";

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of(code, message));
    }
}