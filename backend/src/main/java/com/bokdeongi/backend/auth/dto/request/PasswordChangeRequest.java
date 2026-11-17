package com.bokdeongi.backend.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 비밀번호 변경 요청 DTO.
 *
 * 현재 비밀번호와 새 비밀번호를 전달한다.
 */
public record PasswordChangeRequest(

        @NotBlank(message = "현재 비밀번호는 필수입니다.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호는 필수입니다.")
        @Size(
                min = 8,
                max = 64,
                message = "새 비밀번호는 8~64자여야 합니다."
        )
        String newPassword
) {
}