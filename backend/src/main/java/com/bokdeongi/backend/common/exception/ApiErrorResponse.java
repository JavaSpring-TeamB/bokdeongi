package com.bokdeongi.backend.common.exception;

import java.util.List;

/**
 * 공통 API 오류 응답 형식
 *
 * @param code 비즈니스 에러 코드
 * @param message 사용자에게 표시할 오류 메시지
 * @param fieldErrors 필드별 검증 오류 (선택)
 */
public record ApiErrorResponse(
        String code,
        String message,
        List<FieldErrorDetail> fieldErrors
) {
    public record FieldErrorDetail(
            String field,
            String reason
    ) {}

    // 일반 오류 응답
    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, null);
    }
}