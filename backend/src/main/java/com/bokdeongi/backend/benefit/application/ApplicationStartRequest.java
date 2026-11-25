package com.bokdeongi.backend.benefit.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 신청 시작 또는 기존 신청 이어하기 요청 DTO.
 * 신청 대상 혜택 ID를 전달받는다.
 */
public record ApplicationStartRequest(
        @NotNull(message = "혜택 ID는 필수입니다.")
        @Positive(message = "혜택 ID는 1 이상이어야 합니다.")
        Long benefitId
) {
}