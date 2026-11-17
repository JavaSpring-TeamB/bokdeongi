package com.bokdeongi.backend.profile.dto.request;

import jakarta.validation.constraints.*;
import java.util.List;

/**
 * PROF-01 프로필 3단계: 나의 상황 정보 입력 요청.
 */
public record ProfileStep3Request(

        // 현재 상황 (복수 선택 가능)
        @NotNull(message = "현재 상황은 필수입니다.")
        List<@NotBlank String> statusCodes,

        // 혼인 상태
        @NotBlank(message = "혼인 상태는 필수입니다.")
        @Pattern(
                regexp = "SINGLE|MARRIED|ETC",
                message = "올바르지 않은 혼인 상태입니다."
        )
        String maritalStatus,

        // 자녀 수
        @NotNull(message = "자녀 수는 필수입니다.")
        @Min(value = 0, message = "자녀 수는 0명 이상이어야 합니다.")
        Short childrenCount,

        // 막내 자녀 출생 연도 (자녀가 없는 경우 null)
        Short youngestChildBirthYear,

        // 복지 수급 상태
        @NotBlank(message = "복지 수급 상태는 필수입니다.")
        @Pattern(
                regexp = "BASIC|NEAR_POOR|NONE|UNKNOWN",
                message = "올바르지 않은 복지 수급 상태입니다."
        )
        String welfareStatus

) {}