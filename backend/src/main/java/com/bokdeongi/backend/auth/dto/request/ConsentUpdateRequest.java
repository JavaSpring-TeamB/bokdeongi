package com.bokdeongi.backend.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConsentUpdateRequest(
        /* 변경할 동의 유형 */
        @NotBlank String consentType,

        /* 약관 문서 버전 */
        @NotBlank String docVersion,

        /* 동의 여부 */
        @NotNull Boolean agreed
) {
}