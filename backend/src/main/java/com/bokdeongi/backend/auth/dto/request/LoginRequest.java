package com.bokdeongi.backend.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 일반 로그인 요청 DTO
 * 이메일과 비밀번호를 전달한다.
 */
public record LoginRequest(

        // 로그인 이메일
        @NotBlank
        @Email
        String email,

        // 로그인 비밀번호
        @NotBlank
        String password
) {
}