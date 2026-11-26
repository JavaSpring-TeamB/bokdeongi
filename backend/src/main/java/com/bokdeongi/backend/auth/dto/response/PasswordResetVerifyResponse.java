package com.bokdeongi.backend.auth.dto.response;

/**
 * 이메일 인증번호 검증 성공 응답 DTO.
 *
 * 비밀번호 재설정에 사용할 일회용 토큰과
 * 토큰 유효시간을 반환한다.
 */
public record PasswordResetVerifyResponse(

        String resetToken,
        long expiresIn

) {
}