package com.bokdeongi.backend.auth.dto.response;

/**
 * 비밀번호 재설정 인증번호 발송 요청 응답 DTO.
 *
 * 계정 존재 여부가 노출되지 않도록 공통 안내 메시지와
 * 인증번호 유효시간을 반환한다.
 */
public record PasswordResetCodeResponse(

        String message,
        long expiresIn

) {
}