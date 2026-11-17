package com.bokdeongi.backend.profile.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * PROF-01 프로필 2단계: 가구 정보 입력 요청.
 */
public record ProfileStep2Request(

        @NotNull(message = "가구원 수는 필수입니다.")
        @Min(value = 1, message = "가구원 수는 1명 이상이어야 합니다.")
        Short householdSize,

        // ANNUAL / MONTHLY / BRACKET / UNKNOWN
        @NotBlank(message = "소득 입력 방식은 필수입니다.")
        @Pattern(
                regexp = "ANNUAL|MONTHLY|BRACKET|UNKNOWN",
                message = "올바르지 않은 소득 입력 방식입니다."
        )
        String incomeInputType,

        // 연간 또는 월간 소득 금액 (세전, 원)
        @DecimalMin(value = "0", message = "소득 금액은 0 이상이어야 합니다.")
        BigDecimal incomeAmount,

        // 중위소득 대비 비율 (BRACKET 선택 시 사용)
        @DecimalMin(value = "0", message = "중위소득 비율은 0 이상이어야 합니다.")
        BigDecimal medianIncomePct,

        @NotBlank(message = "주거 형태는 필수입니다.")
        @Pattern(
                regexp = "OWN|JEONSE|MONTHLY|ETC",
                message = "올바르지 않은 주거 형태입니다."
        )
        String housingType,

        @NotNull(message = "무주택 여부는 필수입니다.")
        Boolean noHouse,

        // [] = 해당 없음, null = 미입력
        @NotNull(message = "가구 유형은 필수입니다.")
        List<
                @NotBlank
                @Pattern(
                        regexp = "JA0403|JA0404|JA0411|JA0413|JA0414",
                        message = "올바르지 않은 가구 유형 코드입니다."
                )
                        String
                > householdTypeCodes

) {
}