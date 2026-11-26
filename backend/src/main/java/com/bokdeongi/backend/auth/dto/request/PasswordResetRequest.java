package com.bokdeongi.backend.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 이메일 인증 완료 후 비밀번호 재설정 요청 DTO.
 *
 * 일회용 재설정 토큰과 새로운 비밀번호를 전달받는다.
 */
public record PasswordResetRequest(

        @NotBlank(message = "비밀번호 재설정 토큰이 필요합니다.")
        String resetToken,

        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        @Size(
                min = 8,
                max = 64,
                message = "비밀번호는 8자 이상 64자 이하여야 합니다."
        )
        String newPassword

) {
}