package com.bokdeongi.backend.profile.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

/**
 * PROF-01 프로필 1단계: 기본 정보 입력 요청.
 */
public record ProfileStep1Request(

        @NotNull(message = "생년월일은 필수입니다.")
        @PastOrPresent(message = "생년월일은 미래 날짜일 수 없습니다.")
        LocalDate birthDate,

        @NotBlank(message = "성별은 필수입니다.")
        @Pattern(
                regexp = "M|F",
                message = "성별은 M 또는 F만 입력할 수 있습니다."
        )
        String gender,

        @NotBlank(message = "거주 지역 코드는 필수입니다.")
        @Size(max = 10)
        String regionCode,

        @NotBlank(message = "시·도는 필수입니다.")
        @Size(max = 20)
        String sidoName,

        @Size(max = 20)
        String sigunguName
) {
}