package com.bokdeongi.backend.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 비밀번호 재설정 이메일 인증번호 검증 요청 DTO.
 */
public record PasswordResetVerifyRequest(

        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email,

        @NotBlank(message = "인증번호를 입력해 주세요.")
        @Pattern(
                regexp = "[0-9]{6}",
                message = "인증번호는 숫자 6자리여야 합니다."
        )
        String code

) {
}