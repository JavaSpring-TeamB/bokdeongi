package com.bokdeongi.backend.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 약관 동의 요청 DTO
 * 약관 유형, 문서 버전, 동의 여부를 전달한다.
 */
public record ConsentRequest(

        // 약관 유형 (TERMS, PRIVACY, OPTIONAL_INFO, SENSITIVE)
        @NotBlank
        @Size(max = 15)
        String consentType,

        // 동의한 약관 문서의 버전
        @NotBlank
        @Size(max = 20)
        String docVersion,

        // 동의 여부 (true: 동의, false: 미동의)
        @NotNull
        Boolean agreed
) {
}