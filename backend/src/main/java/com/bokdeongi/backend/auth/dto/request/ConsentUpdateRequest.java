package com.bokdeongi.backend.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 개인정보 동의 변경 및 철회 요청 DTO
 * 선택 개인정보와 민감정보 동의 상태 변경에 사용한다.
 */
public record ConsentUpdateRequest(

        // 변경할 동의 유형 (OPTIONAL_INFO, SENSITIVE)
        @NotBlank
        @Size(max = 15)
        String consentType,

        // 적용할 약관 문서 버전
        @NotBlank
        @Size(max = 20)
        String docVersion,

        // 변경할 동의 상태
        @NotNull
        Boolean agreed
) {
}